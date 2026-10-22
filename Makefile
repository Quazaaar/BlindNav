# BlindNav developer tasks. Gradle needs JDK 21: defaults to Android Studio's bundled JBR.
JAVA_HOME ?= C:/Program Files/Android/Android Studio/jbr
export JAVA_HOME

ifeq ($(OS),Windows_NT)
GRADLEW := .\gradlew.bat
else
GRADLEW := ./gradlew
endif

.PHONY: help build test lint install park replay

help: ## List targets
	@echo "make build                  Assemble the debug APK"
	@echo "make test                   Run JVM unit tests"
	@echo "make lint                   Run Android lint"
	@echo "make install                Install the debug APK on the running emulator/device"
	@echo "make park START=lat,lng     Put the emulator's GPS at a start point"
	@echo "make replay [ARGS=...]      Drive the current trip's route on the emulator"
	@echo "                            e.g. ARGS=\"--detour 120\", \"--pass-by\", \"--noise 5\""

build:
	$(GRADLEW) :app:assembleDebug

test:
	$(GRADLEW) :app:testDebugUnitTest

lint:
	$(GRADLEW) :app:lintDebug

install:
	$(GRADLEW) :app:installDebug

park:
	python tools/replay_route.py park $(START)

replay:
	python tools/replay_route.py drive $(ARGS)
