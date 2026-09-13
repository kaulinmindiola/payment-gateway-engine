package com.paymentgatewayengine.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Verifica CON-002 / DEC-018 / REQ-MAINT-001: el paquete domain/ no debe
 * depender de Spring ni de JPA. Falla el build si esta regla se rompe,
 * en cualquier fase posterior a esta.
 */
@AnalyzeClasses(
        packages = "com.paymentgatewayengine",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_jpa =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..");
}
