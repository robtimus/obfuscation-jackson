/*
 * PropertyConfig.java
 * Copyright 2020 Rob Spoor
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

package com.github.robtimus.obfuscation.jackson;

import java.util.Objects;
import com.github.robtimus.obfuscation.Obfuscator;

final class PropertyConfig {

    final Obfuscator obfuscator;
    final boolean obfuscateObjects;
    final boolean obfuscateArrays;
    final boolean performObfuscation;

    PropertyConfig(Obfuscator obfuscator, boolean obfuscateObjects, boolean obfuscateArrays) {
        this.obfuscator = Objects.requireNonNull(obfuscator);
        this.obfuscateObjects = obfuscateObjects;
        this.obfuscateArrays = obfuscateArrays;
        this.performObfuscation = !obfuscator.equals(Obfuscator.none());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        PropertyConfig other = (PropertyConfig) o;
        return obfuscator.equals(other.obfuscator)
                && obfuscateObjects == other.obfuscateObjects
                && obfuscateArrays == other.obfuscateArrays;
    }

    @Override
    public int hashCode() {
        return obfuscator.hashCode() ^ Boolean.hashCode(obfuscateObjects) ^ Boolean.hashCode(obfuscateArrays);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return "[obfuscator=" + obfuscator
                + ",obfuscateObjects=" + obfuscateObjects
                + ",obfuscateArrays=" + obfuscateArrays
                + "]";
    }
}
