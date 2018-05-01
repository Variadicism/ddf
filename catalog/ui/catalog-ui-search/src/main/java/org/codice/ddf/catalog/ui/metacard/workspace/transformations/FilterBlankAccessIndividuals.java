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
package org.codice.ddf.catalog.ui.metacard.workspace.transformations;

import ddf.catalog.data.types.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.catalog.ui.metacard.workspace.transformer.WorkspaceTransformer;
import org.codice.ddf.catalog.ui.metacard.workspace.transformer.WorkspaceValueTransformation;

public class FilterBlankAccessIndividuals implements WorkspaceValueTransformation<List, List> {
  @Override
  public String getKey() {
    return Security.ACCESS_INDIVIDUALS;
  }

  @Override
  public Class<List> getExpectedMetacardType() {
    return List.class;
  }

  @Override
  public Class<List> getExpectedJsonType() {
    return List.class;
  }

  @Override
  public List metacardValueToJsonValue(WorkspaceTransformer transformer, List metacardValue) {
    return ((List<String>) metacardValue)
        .stream()
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List jsonValueToMetacardValue(WorkspaceTransformer transformer, List jsonValue) {
    return jsonValue;
  }
}
