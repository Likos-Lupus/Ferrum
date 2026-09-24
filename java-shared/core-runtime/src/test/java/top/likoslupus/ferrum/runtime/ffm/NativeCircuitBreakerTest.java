package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.api.ModuleId;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeCircuitBreakerTest {

    @Test
    void disablesModuleAfterThreeConsecutiveInternalFailures() {
        var breaker = new NativeCircuitBreaker();

        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);
        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);
        assertFalse(breaker.isModuleDisabled(ModuleId.NBT));

        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);
        assertTrue(breaker.isModuleDisabled(ModuleId.NBT));
        assertTrue(breaker.isTripped(ModuleId.NBT));
        assertFalse(breaker.isTripped(ModuleId.CODEC));
    }

    @Test
    void successResetsTheInternalFailureStreak() {
        var breaker = new NativeCircuitBreaker();

        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);
        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);
        breaker.recordSuccess(ModuleId.NBT);
        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);
        breaker.recordFailure(ModuleId.NBT, NativeStatus.INTERNAL);

        assertFalse(breaker.isModuleDisabled(ModuleId.NBT));
    }

    @Test
    void malformedInputNeverCounts() {
        var breaker = new NativeCircuitBreaker();

        IntStream.range(0, 10)
                .forEach(_ -> breaker.recordFailure(
                        ModuleId.NBT,
                        NativeStatus.MALFORMED_INPUT
                ));

        assertFalse(breaker.isModuleDisabled(ModuleId.NBT));
        assertFalse(breaker.isSessionTripped());
    }

    @Test
    void panicDisablesTheWholeSession() {
        var breaker = new NativeCircuitBreaker();

        breaker.recordFailure(ModuleId.NBT, NativeStatus.PANIC);

        assertTrue(breaker.isSessionTripped());
        assertTrue(breaker.isTripped(ModuleId.CODEC));
    }

}
