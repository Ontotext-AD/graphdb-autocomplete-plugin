package com.ontotext.trree.plugin.autocomplete;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import org.eclipse.rdf4j.model.IRI;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Pavel Mihaylov on 13/08/2020.
 */
public class WellKnownEntities {
    public static Set<IRI> getWellKnownEntities() throws IOException {
        final ImmutableSet<ClassPath.ClassInfo> vocabularyClasses = ClassPath
                .from(WellKnownEntities.class.getClassLoader())
                .getTopLevelClasses("org.eclipse.rdf4j.model.vocabulary");
        Set<IRI> result = new HashSet<>();
        for (ClassPath.ClassInfo classInfo : vocabularyClasses) {
            result.addAll(getEntitiesFromVocabularyClass(classInfo.load()));
        }
        return result;
    }

    public static Set<IRI> getEntitiesFromVocabularyClass(Class<?> klass) {
        final Set<IRI> result = new HashSet<>();
        final Field[] declaredFields = klass.getDeclaredFields();
        for (Field field : declaredFields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers) && field.getType().equals(IRI.class)) {
                try {
                    result.add((IRI) field.get(klass));
                } catch (IllegalAccessException e) {
                    // should not happen
                }
            }
        }
        return result;
    }
}
