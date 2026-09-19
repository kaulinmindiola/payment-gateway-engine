package com.paymentgateway.engine.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(
        packages = "com.paymentgateway",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_depend_on_jpa =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule layers_must_respect_dependency_rule =
            layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Domain").definedBy("..domain..")
                    .layer("Application").definedBy("..application..")
                    .layer("Infrastructure").definedBy("..infrastructure..")

                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()

                    .withOptionalLayers(true);
}