#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef BOOL (^PantauCanInitHandler)(NSURLRequest *request);

/// Adds +canInitWithRequest: and +canonicalRequestForRequest: to the metaclass
/// of `protocolClass` (a Kotlin NSURLProtocol subclass). Kotlin/Native cannot
/// override Objective-C class methods, so the class-side gate lives here and
/// forwards to `handler`. Idempotent; a later call only replaces the handler.
FOUNDATION_EXPORT void PantauInstallProtocolGate(Class protocolClass, PantauCanInitHandler _Nullable handler);

/// Swizzles -[UIResponder motionEnded:withEvent:] exactly once and calls
/// `onShake` for UIEventSubtypeMotionShake. Passing nil disables the callback.
FOUNDATION_EXPORT void PantauShakeInstall(void (^ _Nullable onShake)(void));

/// True when running in the iOS Simulator.
FOUNDATION_EXPORT BOOL PantauIsSimulator(void);

NS_ASSUME_NONNULL_END
