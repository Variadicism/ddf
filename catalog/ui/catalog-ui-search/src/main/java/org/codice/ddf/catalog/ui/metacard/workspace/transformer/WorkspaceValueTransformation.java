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

/**
 * This partial implementation of {@link WorkspaceTransformation} makes the expected metacard
 * attribute name and JSON-style key one and the same. Implementations of this interface are
 * therefore only able to change the value between a metacard attribute and a JSON-style key-value
 * pair; the key remains consistent.
 *
 * @param <M> See {@link WorkspaceTransformation}.
 * @param <J> See {@link WorkspaceTransformation}.
 */
public interface WorkspaceValueTransformation<M, J> extends WorkspaceTransformation<M, J> {
  @Override
  default String getJsonKey() {
    return getKey();
  }

  @Override
  default String getMetacardKey() {
    return getKey();
  }

  /**
   * @return the String representing both the JSON-style key and the metacard attribute name used by
   *     this {@link WorkspaceTransformation}
   */
  String getKey();
}
