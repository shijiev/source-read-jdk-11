/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.internal.joptsimple.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ResourceBundle;

import jdk.internal.joptsimple.ValueConversionException;
import jdk.internal.joptsimple.ValueConverter;

/**
 * Converts command line options to {@link Path} objects and checks the status of the underlying file.
 */
public class PathConverter implements ValueConverter<Path> {
    private final PathProperties[] pathProperties;

    public PathConverter( PathProperties... pathProperties ) {
        this.pathProperties = pathProperties;
    }

    @Override
    public Path convert( String value ) {
        Path path = Paths.get(value);

        if ( pathProperties != null ) {
            for ( PathProperties each : pathProperties ) {
                if ( !each.accept( path ) )
                    throw new ValueConversionException( message( each.getMessageKey(), path.toString() ) );
            }
        }

        return path;
    }

    @Override
    public Class<Path> valueType() {
        return Path.class;
    }

    @Override
    public String valuePattern() {
        return null;
    }

    private String message( String errorKey, String value ) {
        ResourceBundle bundle = ResourceBundle.getBundle( "jdk.internal.joptsimple.ExceptionMessages" );
        Object[] arguments = new Object[] { value, valuePattern() };
        String template = bundle.getString( PathConverter.class.getName() + "." + errorKey + ".message" );
        return new MessageFormat( template ).format( arguments );
    }
}
