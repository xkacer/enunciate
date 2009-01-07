/*
 * Copyright 2006-2008 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.enunciate.modules.jersey;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import java.util.Properties;
import java.util.Map;

/**
 * A namespace prefix mapper.
 *
 * @author Ryan Heaton
 */
public class PrefixMapper extends NamespacePrefixMapper {

  private final Properties ns2prefix;

  public PrefixMapper(Properties prefix2ns) {
    this.ns2prefix = new Properties();
    if (prefix2ns != null) {
      for (Map.Entry<Object, Object> entry : prefix2ns.entrySet()) {
        this.ns2prefix.put(entry.getValue() == null ? "" : entry.getValue(), entry.getKey());
      }
    }
  }

  public String getPreferredPrefix(String nsuri, String suggestion, boolean defaultPossible) {
    return this.ns2prefix.containsKey(nsuri) ? this.ns2prefix.getProperty(nsuri) : suggestion;
  }
}