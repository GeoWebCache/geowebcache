/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Smith, Boundless, 2018
 */
package org.geowebcache.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeThat;

import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Test that a blob store that has external persistence (Such as the file system) does appropriate checks to prevent
 * accidentally overwriting or deleting data unrelated to GWC.
 *
 * @author Kevin Smith, Boundless
 * @param <T>
 */
@RunWith(Theories.class)
public abstract class BlobStoreSuitabilityTest {

    @Rule
    public SuitabilityCheckRule suitability = SuitabilityCheckRule.system();

    protected abstract Matcher<Object> existing();

    protected abstract Matcher<Object> empty();

    public abstract BlobStore create(Object dir) throws Exception;

    static final Class<? extends Exception> EXCEPTION_CLASS = StorageException.class;

    public BlobStoreSuitabilityTest() {
        super();
    }

    @Theory
    public void emptyOk(Object persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        assumeThat(persistenceLocation, empty());

        BlobStore store = create(persistenceLocation);
        assertThat(store, notNullValue(BlobStore.class));
    }

    @Theory
    public void emptyFail(Object persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EMPTY);
        assumeThat(persistenceLocation, not(empty()));
        assertThrows(EXCEPTION_CLASS, () -> create(persistenceLocation));
    }

    @Theory
    public void existingOk(Object persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        assumeThat(persistenceLocation, (either(empty()).or(existing())));

        BlobStore store = create(persistenceLocation);
        assertThat(store, notNullValue(BlobStore.class));
    }

    @Theory
    public void existingFail(Object persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.EXISTING);
        assumeThat(persistenceLocation, not(either(empty()).or(existing())));
        assertThrows(EXCEPTION_CLASS, () -> create(persistenceLocation));
    }

    @Theory
    public void noneOk(Object persistenceLocation) throws Exception {
        suitability.setValue(CompositeBlobStore.StoreSuitabilityCheck.NONE);

        BlobStore store = create(persistenceLocation);
        assertThat(store, notNullValue(BlobStore.class));
    }
}
