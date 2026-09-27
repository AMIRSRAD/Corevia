package ir.corevia;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void moduleBoundariesAreValid() {
        ApplicationModules.of(CoreviaApplication.class).verify();
    }
}
