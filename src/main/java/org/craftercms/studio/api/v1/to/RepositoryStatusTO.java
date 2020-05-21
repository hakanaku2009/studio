/*
 * Copyright (C) 2007-2019 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.studio.api.v1.to;

import java.util.Set;

public class RepositoryStatusTO {

    private Set<String> conflicting;
    private Set<String> uncommittedChanges;
    private boolean clean;

    public Set<String> getConflicting() {
        return conflicting;
    }

    public void setConflicting(Set<String> conflicting) {
        this.conflicting = conflicting;
    }

    public Set<String> getUncommittedChanges() {
        return uncommittedChanges;
    }

    public void setUncommittedChanges(Set<String> uncommittedChanges) {
        this.uncommittedChanges = uncommittedChanges;
    }

    public boolean isClean() {
        return clean;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }
}