module com.github.robtimus.obfuscation.jackson {
    requires transitive com.github.robtimus.obfuscation;
    requires com.fasterxml.jackson.core;
    requires org.slf4j;

    exports com.github.robtimus.obfuscation.jackson;
}
