package me.adamix.lte.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dummy annotation just for nullaway plugin to shut up about few not yet initialized fields
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Initializer {}