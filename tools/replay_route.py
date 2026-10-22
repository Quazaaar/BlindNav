#!/usr/bin/env python3
"""Fake a drive on the Android emulator for BlindNav.

  park LAT,LNG   Put the emulator's GPS at a start point (do this before tapping Start).
  drive          Drive the optimal route of the current trip, one `adb emu geo fix` per second.
                 Debug builds log the route to logcat (tag BlindNavRoute); run this once the
                 Memorize screen is showing. Slows to 3 m/s for the last 150 m so arrival triggers.

Scenario flags for `drive`:
  --speed M/S         cruising speed (default 13.4, about 30 mph)
  --noise M           gaussian GPS noise in metres
  --detour M          shift the middle third of the route M metres sideways (expect a red detour)
  --pass-by           overshoot the destination at 20 m/s, then come back slowly
"""
import argparse
import math
import os
import random
import shutil
import subprocess
import sys
import time

EARTH_RADIUS_M = 6371008.8
KNOTS_PER_MPS = 1 / 0.514444


def find_adb():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.join(
        os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
    for candidate in (os.path.join(sdk, "platform-tools", "adb.exe"), os.path.join(sdk, "platform-tools", "adb")):
        if os.path.isfile(candidate):
            return candidate
    return shutil.which("adb") or sys.exit("adb not found: set ANDROID_HOME or put adb on PATH")


def decode_polyline(encoded):
    points, index, lat, lng = [], 0, 0, 0
    while index < len(encoded):
        for axis in (0, 1):
            result, shift = 0, 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if axis == 0:
                lat += delta
            else:
                lng += delta
        points.append((lat / 1e5, lng / 1e5))
    return points


def distance_m(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(min(1.0, h)))


def offset(p, north_m, east_m):
    m_per_deg = math.radians(1) * EARTH_RADIUS_M
    return (p[0] + north_m / m_per_deg, p[1] + east_m / (m_per_deg * math.cos(math.radians(p[0]))))


def bearing_vector(a, b):
    """Unit (north, east) vector from a to b in local metres."""
    north = (b[0] - a[0]) * math.radians(1) * EARTH_RADIUS_M
    east = (b[1] - a[1]) * math.radians(1) * EARTH_RADIUS_M * math.cos(math.radians(a[0]))
    length = math.hypot(north, east) or 1.0
    return north / length, east / length


def walk(path, step_m):
    """Points every step_m metres along path (plus the final point)."""
    out, carry = [path[0]], 0.0
    for a, b in zip(path, path[1:]):
        seg = distance_m(a, b)
        pos = step_m - carry
        while pos <= seg:
            f = pos / seg
            out.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
            pos += step_m
        carry = (carry + seg) % step_m
    if out[-1] != path[-1]:
        out.append(path[-1])
    return out


def latest_route(adb):
    """Reassemble the newest route logged as 'i/n chunk' lines by debug builds."""
    log = subprocess.run([adb, "logcat", "-d", "-s", "BlindNavRoute:D"], capture_output=True, text=True).stdout
    chunks, complete = [], None
    for line in log.splitlines():
        if "BlindNavRoute: " not in line:
            continue
        header, _, chunk = line.split("BlindNavRoute: ", 1)[1].partition(" ")
        i, n = map(int, header.split("/"))
        if i == 1:
            chunks = []
        chunks.append(chunk)
        if i == n and len(chunks) == n:
            complete = "".join(chunks)
    if not complete:
        sys.exit("No route in logcat yet. Start a trip in a debug build, then run this during Memorize.")
    return decode_polyline(complete)


def geo_fix(adb, p, speed_mps):
    # geo fix <lng> <lat> <altitude> <satellites> <velocity in knots>
    result = subprocess.run([adb, "emu", "geo", "fix", f"{p[1]:.6f}", f"{p[0]:.6f}", "10", "8",
                             f"{speed_mps * KNOTS_PER_MPS:.1f}"], capture_output=True, text=True)
    if result.returncode != 0 or "KO" in result.stdout:
        sys.exit(f"geo fix failed: {result.stdout.strip()} {result.stderr.strip()}")


def drive(args):
    adb = find_adb()
    route = latest_route(adb)
    print(f"Route: {len(route)} points, {sum(map(distance_m, route, route[1:])) / 1000:.2f} km")

    if args.detour:
        n = len(route)
        shifted = []
        for i, p in enumerate(route):
            if n // 3 <= i <= 2 * n // 3:
                north, east = bearing_vector(route[max(i - 1, 0)], route[min(i + 1, n - 1)])
                p = offset(p, -east * args.detour, north * args.detour)  # perpendicular, to the right
            shifted.append(p)
        route = shifted

    total = sum(map(distance_m, route, route[1:]))
    slow_from = max(total - 150, 0)
    plan = []  # (point, speed)
    travelled = 0.0
    fast = walk(route, args.speed)
    for a, b in zip(fast, fast[1:]):
        travelled += distance_m(a, b)
        if travelled < slow_from or args.pass_by:
            plan.append((b, args.speed))
    if args.pass_by:
        north, east = bearing_vector(route[-2], route[-1])
        overshoot = offset(route[-1], north * 200, east * 200)
        plan += [(p, 20.0) for p in walk([route[-1], overshoot], 20.0)[1:]]
        plan += [(p, 3.0) for p in walk([overshoot, route[-1]], 3.0)[1:]]
    else:
        tail = [p for p in walk(route, 3.0) if distance_m(p, route[-1]) <= 150]
        plan += [(p, 3.0) for p in tail]

    for n, (p, speed) in enumerate(plan, 1):
        if args.noise:
            p = offset(p, random.gauss(0, args.noise), random.gauss(0, args.noise))
        geo_fix(adb, p, speed)
        print(f"\r{n}/{len(plan)} fixes, {speed:.0f} m/s   ", end="", flush=True)
        time.sleep(1)
    print("\nDone.")


def park(args):
    lat, lng = (float(v) for v in args.point.split(","))
    geo_fix(find_adb(), (lat, lng), 0.0)
    print(f"Emulator GPS set to {lat}, {lng}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("park", help="set the emulator location")
    p.add_argument("point", help="LAT,LNG")
    p.set_defaults(func=park)
    d = sub.add_parser("drive", help="drive the current trip's route")
    d.add_argument("--speed", type=float, default=13.4)
    d.add_argument("--noise", type=float, default=0.0)
    d.add_argument("--detour", type=float, default=0.0)
    d.add_argument("--pass-by", action="store_true")
    d.set_defaults(func=drive)
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
