/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
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
package dev.ikm.tinkar.common.service;

public class NonExistentValue implements CharSequence {
    private static final String description = "∅";

    private static final NonExistentValue singleton = new NonExistentValue();

    public static NonExistentValue get() {
        return singleton;
    }

    @Override
    public int length() {
        return description.length();
    }

    @Override
    public char charAt(int index) {
        return description.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return description.subSequence(start, end);
    }

    @Override
    public String toString() {
        return description;
    }
}
