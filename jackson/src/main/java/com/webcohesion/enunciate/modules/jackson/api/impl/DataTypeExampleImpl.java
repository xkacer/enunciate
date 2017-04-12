/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webcohesion.enunciate.modules.jackson.api.impl;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.webcohesion.enunciate.EnunciateException;
import com.webcohesion.enunciate.api.datatype.DataTypeReference;
import com.webcohesion.enunciate.facets.FacetFilter;
import com.webcohesion.enunciate.javac.decorations.element.DecoratedElement;
import com.webcohesion.enunciate.javac.decorations.element.ElementUtils;
import com.webcohesion.enunciate.javac.javadoc.JavaDoc;
import com.webcohesion.enunciate.metadata.DocumentationExample;
import com.webcohesion.enunciate.modules.jackson.model.*;
import com.webcohesion.enunciate.modules.jackson.model.types.*;
import com.webcohesion.enunciate.util.TypeHintUtils;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author Ryan Heaton
 */
public class DataTypeExampleImpl extends ExampleImpl {

  private final ObjectTypeDefinition type;
  private final List<DataTypeReference.ContainerType> containers;

  public DataTypeExampleImpl(ObjectTypeDefinition type) {
    this(type, null);
  }

  public DataTypeExampleImpl(ObjectTypeDefinition typeDefinition, List<DataTypeReference.ContainerType> containers) {
    this.type = typeDefinition;
    this.containers = containers == null ? Collections.<DataTypeReference.ContainerType>emptyList() : containers;
  }

  @Override
  public String getBody() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();

    Context context = new Context();
    context.stack = new LinkedList<String>();
    build(node, this.type, context);

    if (this.type.getContext().isWrapRootValue()) {
      ObjectNode wrappedNode = JsonNodeFactory.instance.objectNode();
      wrappedNode.set(this.type.getJsonRootName(), node);
      node = wrappedNode;
    }

    JsonNode outer = node;
    for (DataTypeReference.ContainerType container : this.containers) {
      switch (container) {
        case array:
        case collection:
        case list:
          ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
          arrayNode.add(outer);
          outer = arrayNode;
          break;
        case map:
          ObjectNode mapNode = JsonNodeFactory.instance.objectNode();
          mapNode.set("...", outer);
          outer = mapNode;
          break;
      }
    }

    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    try {
      return mapper.writeValueAsString(outer);
    }
    catch (JsonProcessingException e) {
      throw new EnunciateException(e);
    }
  }

  private void build(ObjectNode node, ObjectTypeDefinition type, Context context) {
    if (context.stack.size() > 2) {
      //don't go deeper than 2 for fear of the OOM (see https://github.com/stoicflame/enunciate/issues/139).
      return;
    }

    if (type.getTypeIdInclusion() == JsonTypeInfo.As.PROPERTY) {
      if (type.getTypeIdProperty() != null) {
        node.put(type.getTypeIdProperty(), "...");
      }
    }

    FacetFilter facetFilter = type.getContext().getContext().getConfiguration().getFacetFilter();
    for (Member member : type.getMembers()) {
      if (!facetFilter.accept(member)) {
        continue;
      }

      if (ElementUtils.findDeprecationMessage(member) != null) {
        continue;
      }

      String example = null;
      String example2 = null;
      JsonType exampleType = null;
      TypeDefinition memberTypeDef = null;

      TypeMirror memberType = member.asType();
      if (memberType instanceof DeclaredType) {
        memberTypeDef = type.getContext().findTypeDefinition(((DeclaredType)memberType).asElement());
      }
      JavaDoc.JavaDocTagList tags = member.getJavaDoc().get("documentationExample");
      if (tags == null && memberTypeDef != null) {
        tags = memberTypeDef.getJavaDoc().get("documentationExample");
      }
      if (tags != null && tags.size() > 0) {
        String tag = tags.get(0).trim();
        example = tag.isEmpty() ? null : tag;
        example2 = example;
        if (tags.size() > 1) {
          tag = tags.get(1).trim();
          example2 = tag.isEmpty() ? null : tag;
        }
      }

      tags = member.getJavaDoc().get("documentationType");
      if (tags == null && memberTypeDef != null) {
        tags = memberTypeDef.getJavaDoc().get("documentationType");
      }
      if (tags == null && member.getTypeDefinition() != null && member.getTypeDefinition().getDelegate() instanceof DecoratedElement) {
        tags = ((DecoratedElement<?>)member.getTypeDefinition().getDelegate()).getJavaDoc().get("documentationType");
      }
      if (tags != null && tags.size() > 0) {
        String tag = tags.get(0).trim();
        if (!tag.isEmpty()) {
          TypeElement typeElement = type.getContext().getContext().getProcessingEnvironment().getElementUtils().getTypeElement(tag);
          if (typeElement != null) {
            exampleType = JsonTypeFactory.getJsonType(typeElement.asType(), type.getContext());
          }
          else {
            type.getContext().getContext().getLogger().warn("Invalid documentation type %s.", tag);
          }
        }
      }

      DocumentationExample documentationExample = member.getAnnotation(DocumentationExample.class);
      if (documentationExample == null && memberTypeDef != null) {
        documentationExample = memberTypeDef.getAnnotation(DocumentationExample.class);
      }
      if (documentationExample != null) {
        if (documentationExample.exclude()) {
          continue;
        }

        example = documentationExample.value();
        example = "##default".equals(example) ? null : example;
        example2 = documentationExample.value2();
        example2 = "##default".equals(example2) ? null : example2;
        TypeMirror typeHint = TypeHintUtils.getTypeHint(documentationExample.type(), type.getContext().getContext().getProcessingEnvironment(), null);
        if (typeHint != null) {
          exampleType = JsonTypeFactory.getJsonType(typeHint, type.getContext());
        }
      }

      if (context.currentIndex % 2 > 0) {
        //if our index is odd, switch example 1 and example 2.
        String placeholder = example2;
        example2 = example;
        example = placeholder;
      }

      if (member.getChoices().size() > 1) {
        if (member.isCollectionType()) {
          final ArrayNode exampleNode = JsonNodeFactory.instance.arrayNode();

          for (Member choice : member.getChoices()) {
            JsonType jsonType = exampleType == null ? choice.getJsonType() : exampleType;
            String choiceName = choice.getName();
            if ("".equals(choiceName)) {
              choiceName = "...";
            }

            if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_ARRAY) {
              ArrayNode wrapperNode = JsonNodeFactory.instance.arrayNode();
              wrapperNode.add(choiceName);
              wrapperNode.add(exampleNode(jsonType, example, example2, context));
              exampleNode.add(wrapperNode);
            }
            else if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_OBJECT) {
              ObjectNode wrapperNode = JsonNodeFactory.instance.objectNode();
              wrapperNode.set(choiceName, exampleNode(jsonType, example, example2, context));
              exampleNode.add(wrapperNode);
            }
            else {
              JsonNode itemNode = exampleNode(jsonType, example, example2, context);

              if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.PROPERTY) {
                if (member.getSubtypeIdProperty() != null && itemNode instanceof ObjectNode) {
                  ((ObjectNode) itemNode).put(member.getSubtypeIdProperty(), "...");
                }
              }
              else if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.EXTERNAL_PROPERTY) {
                if (member.getSubtypeIdProperty() != null) {
                  node.put(member.getSubtypeIdProperty(), "...");
                }
              }

              exampleNode.add(itemNode);
            }
          }

          node.set(member.getName(), exampleNode);
        }
        else {
          for (Member choice : member.getChoices()) {
            JsonNode exampleNode;
            JsonType jsonType = exampleType == null ? choice.getJsonType() : exampleType;
            String choiceName = choice.getName();
            if ("".equals(choiceName)) {
              choiceName = "...";
            }

            if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_ARRAY) {
              ArrayNode wrapperNode = JsonNodeFactory.instance.arrayNode();
              wrapperNode.add(choiceName);
              wrapperNode.add(exampleNode(jsonType, example, example2, context));
              exampleNode = wrapperNode;
            }
            else if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.WRAPPER_OBJECT) {
              ObjectNode wrapperNode = JsonNodeFactory.instance.objectNode();
              wrapperNode.set(choiceName, exampleNode(jsonType, example, example2, context));
              exampleNode = wrapperNode;
            }
            else {
              exampleNode = exampleNode(jsonType, example, example2, context);

              if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.PROPERTY) {
                if (member.getSubtypeIdProperty() != null && exampleNode instanceof ObjectNode) {
                  ((ObjectNode) exampleNode).put(member.getSubtypeIdProperty(), "...");
                }
              }
              else if (member.getSubtypeIdInclusion() == JsonTypeInfo.As.EXTERNAL_PROPERTY) {
                if (member.getSubtypeIdProperty() != null) {
                  node.put(member.getSubtypeIdProperty(), "...");
                }
              }
            }

            node.set(member.getName(), exampleNode);
          }
        }
      }
      else {
        JsonType jsonType = exampleType == null ? member.getJsonType() : exampleType;
        node.set(member.getName(), exampleNode(jsonType, example, example2, context));
      }
    }

    JsonType supertype = type.getSupertype();
    if (supertype instanceof JsonClassType && ((JsonClassType)supertype).getTypeDefinition() instanceof ObjectTypeDefinition) {
      build(node, (ObjectTypeDefinition) ((JsonClassType) supertype).getTypeDefinition(), context);
    }

    if (type.getWildcardMember() != null && ElementUtils.findDeprecationMessage(type.getWildcardMember()) == null) {
      node.put("extension1", "...");
      node.put("extension2", "...");
    }

  }

  private JsonNode exampleNode(JsonType jsonType, String specifiedExample, String specifiedExample2, Context context) {
    if (jsonType instanceof JsonClassType) {
      TypeDefinition typeDefinition = ((JsonClassType) jsonType).getTypeDefinition();
      if (typeDefinition instanceof ObjectTypeDefinition) {
        ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
        if (!context.stack.contains(typeDefinition.getQualifiedName().toString())) {
          context.stack.push(typeDefinition.getQualifiedName().toString());
          try {
            build(objectNode, (ObjectTypeDefinition) typeDefinition, context);
          }
          finally {
            context.stack.pop();
          }
        }
        return objectNode;
      }
      else if (typeDefinition instanceof EnumTypeDefinition) {
        String example = "???";

        if (specifiedExample != null) {
          example = specifiedExample;
        }
        else {
          List<EnumValue> enumValues = ((EnumTypeDefinition) typeDefinition).getEnumValues();
          if (enumValues.size() > 0) {
            int index = new Random().nextInt(enumValues.size());
            example = enumValues.get(index).getValue();
          }
        }

        return JsonNodeFactory.instance.textNode(example);
      }
      else {
        return exampleNode(((SimpleTypeDefinition) typeDefinition).getBaseType(), specifiedExample, specifiedExample2, context);
      }
    }
    else if (jsonType instanceof JsonMapType) {
      ObjectNode mapNode = JsonNodeFactory.instance.objectNode();
      JsonType valueType = ((JsonMapType) jsonType).getValueType();
      mapNode.set("property1", exampleNode(valueType, specifiedExample, specifiedExample2, context));
      Context context2 = new Context();
      context2.stack = context.stack;
      context2.currentIndex = 1;
      mapNode.set("property2", exampleNode(valueType, specifiedExample, specifiedExample2, context2));
      return mapNode;
    }
    else if (jsonType.isArray()) {
      ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
      if (jsonType instanceof JsonArrayType) {
        JsonNode componentNode = exampleNode(((JsonArrayType) jsonType).getComponentType(), specifiedExample, specifiedExample2, context);
        arrayNode.add(componentNode);
        Context context2 = new Context();
        context2.stack = context.stack;
        context2.currentIndex = 1;
        JsonNode componentNode2 = exampleNode(((JsonArrayType) jsonType).getComponentType(), specifiedExample2, specifiedExample, context2);
        arrayNode.add(componentNode2);
      }
      return arrayNode;
    }
    else if (jsonType.isWholeNumber()) {
      Long example = 12345L;
      if (specifiedExample != null) {
        try {
          example = Long.parseLong(specifiedExample);
        }
        catch (NumberFormatException e) {
          this.type.getContext().getContext().getLogger().warn("\"%s\" was provided as a documentation example, but it is not a valid JSON whole number, so it will be ignored.", specifiedExample);
        }
      }
      return JsonNodeFactory.instance.numberNode(example);
    }
    else if (jsonType.isNumber()) {
      Double example = 12345D;
      if (specifiedExample != null) {
        try {
          example = Double.parseDouble(specifiedExample);
        }
        catch (NumberFormatException e) {
          this.type.getContext().getContext().getLogger().warn("\"%s\" was provided as a documentation example, but it is not a valid JSON number, so it will be ignored.", specifiedExample);
        }
      }
      return JsonNodeFactory.instance.numberNode(example);
    }
    else if (jsonType.isBoolean()) {
      boolean example = !"false".equals(specifiedExample);
      return JsonNodeFactory.instance.booleanNode(example);
    }
    else if (jsonType.isString()) {
      String example = specifiedExample;
      if (example == null) {
        example = "...";
      }
      return JsonNodeFactory.instance.textNode(example);
    }
    else {
      return JsonNodeFactory.instance.objectNode();
    }
  }

  private static class Context {
    LinkedList<String> stack;
    int currentIndex = 0;
  }
}
