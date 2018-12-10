package com.novoda.staticanalysis.internal.spotbugs

import com.novoda.staticanalysis.Violations
import com.novoda.staticanalysis.internal.Configurator

final class SpotBugsConfiguratorFactory {

    static Configurator create(project, violationsContainer, evaluateViolations) {
        Violations violations = violationsContainer.maybeCreate('SpotBugs')
        return new SpotBugsConfigurator(project, violations, evaluateViolations)
    }
}
