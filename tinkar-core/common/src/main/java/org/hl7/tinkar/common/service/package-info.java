package org.hl7.tinkar.common.service;

/**
 *
 * ServiceLoader NO dependencies. It only needs Java.
 * <br/>
 * The startup times for ServiceLoader are MUCH faster than for DI frameworks.
 * This speed is thanks to less code to load, no scanning, no reflection, no big frameworks.
 * <br/>
 * One of the key aspects to java modules was the ability to completely firewall off classes from
 * code outside the module. ServiceLoader is the mechanism that allows outside code to “access”
 * internal implementations. Java modules allow you to register services for internal
 * implementations while still maintaining the firewall.
 * <br/>
 * In fact, this is the only officially approved mechanism of supporting dependency injection
 * with Java Modules. Spring and most existing DI frameworks rely on reflection to discovery
 * and wire their components up. But this breaks down with Java Modules. Not even reflection
 * can see into modules (unless you let it, but why would you).
 * <br/>
 * https://itnext.io/serviceloader-the-built-in-di-framework-youve-probably-never-heard-of-1fa68a911f9b
 * <br/>
 * https://github.com/google/auto/tree/master/service
 */