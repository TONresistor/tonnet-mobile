package com.tonnet.browser.web

internal object JavaScriptPrivacyPolicy {
    fun documentStartScript(
        antiFingerprintingEnabled: Boolean,
        sessionSeed: String,
    ): String {
        require(SESSION_SEED.matches(sessionSeed))
        val protection = if (antiFingerprintingEnabled) {
            ANTI_FINGERPRINTING_SCRIPT.replace(SESSION_SEED_PLACEHOLDER, sessionSeed)
        } else {
            ""
        }
        return BASE_SCRIPT.replace(PROTECTION_PLACEHOLDER, protection)
    }

    private const val BASE_SCRIPT = """
        (() => {
          'use strict';

          const nativeFunctions = new WeakMap();
          const nativeToString = Function.prototype.toString;

          const findPropertyOwner = (target, name) => {
            let current = target;
            while (current) {
              if (Object.prototype.hasOwnProperty.call(current, name)) {
                return current;
              }
              current = Object.getPrototypeOf(current);
            }
            return null;
          };

          const wrapFunction = (original, invoke) => {
            const replacement = new Proxy(original, {
              apply(_target, thisArgument, argumentsList) {
                return invoke(thisArgument, argumentsList);
              }
            });
            nativeFunctions.set(replacement, original);
            return replacement;
          };

          const toStringOwner = findPropertyOwner(Function.prototype, 'toString');
          const toStringDescriptor =
            toStringOwner && Object.getOwnPropertyDescriptor(toStringOwner, 'toString');
          if (toStringOwner && toStringDescriptor && 'value' in toStringDescriptor) {
            try {
              const protectedToString = wrapFunction(
                nativeToString,
                (thisArgument) =>
                  nativeToString.call(nativeFunctions.get(thisArgument) || thisArgument)
              );
              Object.defineProperty(toStringOwner, 'toString', {
                ...toStringDescriptor,
                value: protectedToString
              });
            } catch (_) {}
          }

          const replaceExisting = (target, name, value) => {
            if (!target) return;
            const owner = findPropertyOwner(target, name);
            if (!owner) return;
            const descriptor = Object.getOwnPropertyDescriptor(owner, name);
            if (!descriptor) return;
            try {
              if ('value' in descriptor) {
                Object.defineProperty(owner, name, {
                  ...descriptor,
                  value
                });
                return;
              }
              if (typeof descriptor.get !== 'function') return;
              const getter = wrapFunction(descriptor.get, () => value);
              Object.defineProperty(owner, name, {
                ...descriptor,
                get: getter
              });
            } catch (_) {}
          };

          const wrapMethod = (prototype, name, factory) => {
            if (!prototype) return;
            const owner = findPropertyOwner(prototype, name);
            if (!owner) return;
            const descriptor = Object.getOwnPropertyDescriptor(owner, name);
            if (!descriptor || !('value' in descriptor)) return;
            const original = descriptor.value;
            if (typeof original !== 'function') return;
            const implementation = factory(original);
            if (typeof implementation !== 'function') return;
            try {
              Object.defineProperty(owner, name, {
                ...descriptor,
                value: wrapFunction(
                  original,
                  (thisArgument, argumentsList) =>
                    Reflect.apply(implementation, thisArgument, argumentsList)
                )
              });
            } catch (_) {}
          };

          const disableGlobal = (name) => replaceExisting(globalThis, name, undefined);
          [
            'RTCPeerConnection',
            'webkitRTCPeerConnection',
            'WebTransport',
            'WebSocket',
            'Worker',
            'SharedWorker',
            'DeviceMotionEvent',
            'DeviceOrientationEvent',
            'Sensor',
            'AmbientLightSensor',
            'Accelerometer',
            'LinearAccelerationSensor',
            'GravitySensor',
            'Gyroscope',
            'Magnetometer',
            'AbsoluteOrientationSensor',
            'RelativeOrientationSensor',
            'Bluetooth',
            'USB',
            'Serial',
            'HID',
            'NDEFReader',
            'XRSession',
            'XRSystem',
            'XRWebGLLayer',
            'GPU',
            'GPUAdapter',
            'GPUDevice',
            'GPUQueue',
            'GPUBuffer',
            'GPUTexture',
            'GPUCanvasContext',
            'SpeechRecognition',
            'webkitSpeechRecognition',
            'SpeechSynthesisUtterance'
          ].forEach(disableGlobal);

          const nav = globalThis.navigator;
          const replaceNavigator = (name, value) => replaceExisting(nav, name, value);

          if (nav) {
            [
              'bluetooth',
              'usb',
              'serial',
              'hid',
              'xr',
              'gpu',
              'mediaDevices',
              'connection',
              'userAgentData',
              'getBattery',
              'getGamepads'
            ].forEach((name) => replaceNavigator(name, undefined));

            replaceNavigator('globalPrivacyControl', true);
            replaceNavigator('doNotTrack', '1');

            try {
              const serviceWorker = nav.serviceWorker;
              wrapMethod(
                serviceWorker && Object.getPrototypeOf(serviceWorker),
                'register',
                () => function () {
                  return Promise.reject(
                    new DOMException('Disabled by privacy policy', 'NotAllowedError')
                  );
                }
              );
            } catch (_) {}
          }

          replaceExisting(globalThis, 'speechSynthesis', undefined);
          __ADVANCED_PROTECTION__
        })();
    """

    private const val ANTI_FINGERPRINTING_SCRIPT = """
          const hashText = (text) => {
            let hash = 2166136261;
            for (let index = 0; index < text.length; index += 1) {
              hash ^= text.charCodeAt(index);
              hash = Math.imul(hash, 16777619);
            }
            hash += hash << 13;
            hash ^= hash >>> 7;
            hash += hash << 3;
            hash ^= hash >>> 17;
            hash += hash << 5;
            return hash >>> 0;
          };

          const documentNonce = (() => {
            try {
              const values = new Uint32Array(4);
              globalThis.crypto.getRandomValues(values);
              return Array.from(values).join('-');
            } catch (_) {
              return String(Math.random()) + '-' + String(Date.now());
            }
          })();

          const siteContext = (() => {
            try {
              const location = globalThis.location;
              const ancestors = location && location.ancestorOrigins;
              const candidate =
                ancestors && ancestors.length
                  ? ancestors[ancestors.length - 1]
                  : location && location.origin;
              if (candidate && candidate !== 'null') {
                return new URL(candidate).origin.toLowerCase();
              }
            } catch (_) {}
            return 'opaque:' + documentNonce;
          })();

          const seedFor = (surface) =>
            hashText('__SESSION_SEED__|' + siteContext + '|' + surface);

          const perturbBytes = (values, surface) => {
            if (!values || typeof values.length !== 'number' || values.length === 0) {
              return values;
            }
            const seed = seedFor(surface);
            const start = seed % Math.min(3, values.length);
            for (let index = start; index < values.length; index += 64) {
              values[index] = values[index] ^ 1;
            }
            return values;
          };

          const perturbFloats = (values, surface) => {
            if (!values || typeof values.length !== 'number' || values.length === 0) {
              return values;
            }
            const seed = seedFor(surface);
            const direction = (seed & 1) === 0 ? 1 : -1;
            const start = seed % Math.min(8, values.length);
            for (let index = start; index < values.length; index += 32) {
              if (Number.isFinite(values[index])) {
                values[index] += direction * 0.0000001;
              }
            }
            return values;
          };

          if (nav) {
            const hardwareSeed = seedFor('hardware');
            replaceNavigator('hardwareConcurrency', (hardwareSeed & 1) === 0 ? 2 : 4);
            if ('deviceMemory' in nav) {
              replaceNavigator('deviceMemory', (hardwareSeed & 2) === 0 ? 2 : 4);
            }
          }

          const canvasPrototype =
            globalThis.HTMLCanvasElement && globalThis.HTMLCanvasElement.prototype;
          const context2DPrototype =
            globalThis.CanvasRenderingContext2D &&
            globalThis.CanvasRenderingContext2D.prototype;
          const originalGetContext = canvasPrototype && canvasPrototype.getContext;
          const originalToDataUrl = canvasPrototype && canvasPrototype.toDataURL;
          const originalToBlob = canvasPrototype && canvasPrototype.toBlob;
          const originalGetImageData =
            context2DPrototype && context2DPrototype.getImageData;
          const originalPutImageData =
            context2DPrototype && context2DPrototype.putImageData;
          const originalDrawImage = context2DPrototype && context2DPrototype.drawImage;

          const protectedCanvasCopy = (source) => {
            if (
              !source ||
              !source.width ||
              !source.height ||
              source.width * source.height > 4194304 ||
              typeof originalGetContext !== 'function' ||
              typeof originalGetImageData !== 'function' ||
              typeof originalPutImageData !== 'function' ||
              typeof originalDrawImage !== 'function'
            ) {
              return null;
            }
            const copy = document.createElement('canvas');
            copy.width = source.width;
            copy.height = source.height;
            const context = originalGetContext.call(copy, '2d');
            if (!context) return null;
            originalDrawImage.call(context, source, 0, 0);
            const imageData = originalGetImageData.call(
              context,
              0,
              0,
              copy.width,
              copy.height
            );
            perturbBytes(imageData.data, 'canvas');
            originalPutImageData.call(context, imageData, 0, 0);
            return copy;
          };

          wrapMethod(context2DPrototype, 'getImageData', (original) =>
            function () {
              const imageData = original.apply(this, arguments);
              perturbBytes(imageData && imageData.data, 'canvas');
              return imageData;
            }
          );

          wrapMethod(canvasPrototype, 'toDataURL', (original) =>
            function () {
              try {
                const copy = protectedCanvasCopy(this);
                if (copy && typeof originalToDataUrl === 'function') {
                  return originalToDataUrl.apply(copy, arguments);
                }
              } catch (_) {}
              return original.apply(this, arguments);
            }
          );

          wrapMethod(canvasPrototype, 'toBlob', (original) =>
            function () {
              try {
                const copy = protectedCanvasCopy(this);
                if (copy && typeof originalToBlob === 'function') {
                  return originalToBlob.apply(copy, arguments);
                }
              } catch (_) {}
              return original.apply(this, arguments);
            }
          );

          const protectWebGL = (constructor) => {
            const prototype = constructor && constructor.prototype;
            wrapMethod(prototype, 'getExtension', (original) =>
              function (name) {
                if (
                  typeof name === 'string' &&
                  name.toLowerCase() === 'webgl_debug_renderer_info'
                ) {
                  return null;
                }
                return original.apply(this, arguments);
              }
            );
            wrapMethod(prototype, 'getParameter', (original) =>
              function (parameter) {
                if (parameter === 37445 || parameter === 37446) {
                  return null;
                }
                return original.apply(this, arguments);
              }
            );
            wrapMethod(prototype, 'readPixels', (original) =>
              function () {
                const result = original.apply(this, arguments);
                const destination = arguments[6];
                if (destination && ArrayBuffer.isView(destination)) {
                  const bytes = new Uint8Array(
                    destination.buffer,
                    destination.byteOffset,
                    destination.byteLength
                  );
                  perturbBytes(bytes, 'webgl');
                }
                return result;
              }
            );
          };

          protectWebGL(globalThis.WebGLRenderingContext);
          protectWebGL(globalThis.WebGL2RenderingContext);

          const protectedAudioBuffers = new WeakSet();
          const protectedAudioChannels = new WeakMap();
          const offlineAudioPrototype =
            globalThis.OfflineAudioContext && globalThis.OfflineAudioContext.prototype;
          const audioBufferPrototype =
            globalThis.AudioBuffer && globalThis.AudioBuffer.prototype;
          const analyserPrototype =
            globalThis.AnalyserNode && globalThis.AnalyserNode.prototype;

          wrapMethod(offlineAudioPrototype, 'startRendering', (original) =>
            function () {
              const rendering = original.apply(this, arguments);
              if (!rendering || typeof rendering.then !== 'function') {
                return rendering;
              }
              return rendering.then((buffer) => {
                if (buffer) protectedAudioBuffers.add(buffer);
                return buffer;
              });
            }
          );

          wrapMethod(audioBufferPrototype, 'getChannelData', (original) =>
            function (channel) {
              const data = original.apply(this, arguments);
              if (!protectedAudioBuffers.has(this)) return data;
              let channels = protectedAudioChannels.get(this);
              if (!channels) {
                channels = new Set();
                protectedAudioChannels.set(this, channels);
              }
              if (!channels.has(channel)) {
                perturbFloats(data, 'audio-' + channel);
                channels.add(channel);
              }
              return data;
            }
          );

          wrapMethod(audioBufferPrototype, 'copyFromChannel', (original) =>
            function (destination, channel) {
              const result = original.apply(this, arguments);
              if (protectedAudioBuffers.has(this)) {
                perturbFloats(destination, 'audio-' + channel);
              }
              return result;
            }
          );

          [
            'getByteFrequencyData',
            'getByteTimeDomainData'
          ].forEach((name) => {
            wrapMethod(analyserPrototype, name, (original) =>
              function (destination) {
                const result = original.apply(this, arguments);
                perturbBytes(destination, 'audio-analyser-' + name);
                return result;
              }
            );
          });

          [
            'getFloatFrequencyData',
            'getFloatTimeDomainData'
          ].forEach((name) => {
            wrapMethod(analyserPrototype, name, (original) =>
              function (destination) {
                const result = original.apply(this, arguments);
                perturbFloats(destination, 'audio-analyser-' + name);
                return result;
              }
            );
          });
    """

    private val SESSION_SEED = Regex("[0-9a-f]{32}")
    private const val SESSION_SEED_PLACEHOLDER = "__SESSION_SEED__"
    private const val PROTECTION_PLACEHOLDER = "__ADVANCED_PROTECTION__"
}
