/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic

inline fun <reified T> MutableList<T>.forEachSupport(skipFirst: Int = 0, operator: (T) -> Unit) {
    val li = listIterator()
    var skip = skipFirst
    while (skip-- > 0) {
        li.next()
    }
    while (li.hasNext()) {
        operator(li.next())
    }
}

inline fun <reified T> MutableList<T>.replaceAllSupport(skipFirst: Int = 0, operator: (T) -> T) {
    val li = listIterator()
    var skip = skipFirst
    while (skip-- > 0) {
        li.next()
    }
    while (li.hasNext()) {
        li.set(operator(li.next()))
    }
}
