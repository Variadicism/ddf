/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.catalog.ui.metacard.workspace.transformer;

import javax.annotation.Nullable;

/**
 * A representation of one key-value transformation for the {@link WorkspaceTransformer}.
 * Implementations of this interface can be used to transform JSON-style key-value pairs into
 * metacard attributes and vice versa, especially from and into workspace metacards.
 *
 * @param <M> the expected type of metacard values handled by this {@link WorkspaceTransformation}
 * @param <J> the expected type of JSON-style key-value pair values handled by this {@link
 *     WorkspaceTransformation}
 */
public interface WorkspaceTransformation<M, J> {
  /**
   * @return the metacard attribute name associated with both the metacard value passed into {@link
   *     #metacardValueToJsonValue(WorkspaceTransformer, Object) metacardValueToJsonValue} and the
   *     metacard value returned from {@link #jsonValueToMetacardValue(WorkspaceTransformer, Object)
   *     jsonValueToMetacardValue}
   */
  String getMetacardKey();

  /**
   * @return the JSON-style key associated with both the JSON-style value value passed into {@link
   *     #jsonValueToMetacardValue(WorkspaceTransformer, Object) jsonValueToMetacardValue} and the
   *     JSON-style value returned from {@link #metacardValueToJsonValue(WorkspaceTransformer,
   *     Object) metacardValueToJsonValue}
   */
  String getJsonKey();

  /**
   * @return the class that the given and returned metacard values are expected to be an instance of
   */
  Class<M> getExpectedMetacardType();

  /**
   * @return the class that the given and returned JSON-style values are expected to be an instance
   *     of
   */
  Class<J> getExpectedJsonType();

  /**
   * The method by which this {@link WorkspaceTransformation} transforms a metacard attribute value
   * into a corresponding value for a key-value pair for a JSON-style data map. Returning
   * <b><tt>null</tt></b> will result in no corresponding JSON-style key-value pair being added to
   * the final JSON-style data map transformation product.
   *
   * @param transformer the {@link WorkspaceTransformer} that is transforming the given metacard
   *     value
   * @param metacardValue the metacard attribute value to be transformed; this will be an instance
   *     of this {@link WorkspaceTransformation}'s {@link #getExpectedMetacardType() expected
   *     metacard type} and will come from a metacard's attribute with the given {@link
   *     #getMetacardKey() expected metacard key}.
   * @return a new value to be used as the value in a JSON-style data map with this {@link
   *     WorkspaceTransformation}'s {@link #getJsonKey() JSON key}; this will be an instance of this
   *     {@link WorkspaceTransformation}'s {@link #getExpectedJsonType() expected JSON type}
   */
  @Nullable
  J metacardValueToJsonValue(WorkspaceTransformer transformer, M metacardValue);

  /**
   * The method by which this {@link WorkspaceTransformation} transforms value for a key-value pair
   * for a JSON-style data map into a corresponding a metacard attribute value. Returning
   * <b><tt>null</tt></b> will result in no corresponding metacard attribute being added to the
   * final metacard transformation product.
   *
   * @param transformer the {@link WorkspaceTransformer} that is transforming the given JSON-style
   *     value
   * @param jsonValue the JSON-style value to be transformed; this will be an instance of this
   *     {@link WorkspaceTransformation}'s {@link #getExpectedJsonType() expected JSON type} and
   *     will come from a JSON-style data map's key-value pair with the given {@link #getJsonKey()
   *     expected JSON key}.
   * @return a new value to be used as the value in a metacard attribute with this {@link
   *     WorkspaceTransformation}'s {@link #getMetacardKey() metacard key}; this will be an instance
   *     of this {@link WorkspaceTransformation}'s {@link #getExpectedMetacardType() expected
   *     metacard type}
   */
  @Nullable
  M jsonValueToMetacardValue(WorkspaceTransformer transformer, J jsonValue);
}
