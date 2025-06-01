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
 * <p>Copyright 2018
 */
package org.geowebcache.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;

public class ListenerCollectionTest {

    @Test
    public void testEmpty() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();

        collection.safeForEach((x) -> {
            fail("should not be called");
        });
    }

    @Test
    public void testCallsListener() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();

        Runnable l1 = EasyMock.createMock("l1", Runnable.class);

        l1.run();
        EasyMock.expectLastCall().once();

        EasyMock.replay(l1);

        collection.add(l1);

        collection.safeForEach(Runnable::run);

        EasyMock.verify(l1);
    }

    @Test
    public void testCallsListeners() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();
        IMocksControl control = EasyMock.createControl();
        Runnable l1 = control.createMock("l1", Runnable.class);
        Runnable l2 = control.createMock("l2", Runnable.class);

        control.checkOrder(true);
        l1.run();
        EasyMock.expectLastCall().once();
        l2.run();
        EasyMock.expectLastCall().once();

        control.replay();

        collection.add(l1);
        collection.add(l2);

        collection.safeForEach(Runnable::run);

        control.verify();
    }

    @Test
    public void testRemove() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();
        IMocksControl control = EasyMock.createControl();
        Runnable l1 = control.createMock("l1", Runnable.class);
        Runnable l2 = control.createMock("l2", Runnable.class);

        control.checkOrder(true);
        l2.run();
        EasyMock.expectLastCall().once();

        control.replay();

        collection.add(l1);
        collection.add(l2);
        collection.remove(l1);

        collection.safeForEach(Runnable::run);

        control.verify();
    }

    @Test
    public void testException() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();
        IMocksControl control = EasyMock.createControl();
        Runnable l1 = control.createMock("l1", Runnable.class);

        Exception e1 = new IllegalArgumentException();
        control.checkOrder(true);

        l1.run();
        EasyMock.expectLastCall().andThrow(e1);

        control.replay();

        collection.add(l1);
        Exception exception = assertThrows(Exception.class, () -> collection.safeForEach(Runnable::run));
        assertThat(exception, sameInstance(e1));
        control.verify();
    }

    @Test
    public void testExceptionDoesntPreventOthers() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();
        IMocksControl control = EasyMock.createControl();
        Runnable l1 = control.createMock("l1", Runnable.class);
        Runnable l2 = control.createMock("l2", Runnable.class);

        Exception e1 = new IllegalArgumentException();
        control.checkOrder(true);

        l1.run();
        EasyMock.expectLastCall().andThrow(e1);
        l2.run();
        EasyMock.expectLastCall().once();

        control.replay();

        collection.add(l1);
        collection.add(l2);
        Exception exception = assertThrows(Exception.class, () -> collection.safeForEach(Runnable::run));
        assertThat(exception, sameInstance(e1));
        control.verify();
    }

    @Test
    public void testSuppressedExceptionsRecorded() throws Exception {
        ListenerCollection<Runnable> collection = new ListenerCollection<>();
        IMocksControl control = EasyMock.createControl();
        Runnable l1 = control.createMock("l1", Runnable.class);
        Runnable l2 = control.createMock("l2", Runnable.class);

        Exception e1 = new IllegalArgumentException();
        Exception e2 = new IllegalArgumentException();
        control.checkOrder(true);

        l1.run();
        EasyMock.expectLastCall().andThrow(e1);
        l2.run();
        EasyMock.expectLastCall().andThrow(e2);

        control.replay();

        collection.add(l1);
        collection.add(l2);
        Exception exception = assertThrows(Exception.class, () -> collection.safeForEach(Runnable::run));
        assertThat(exception, both(sameInstance(e2)).and(hasProperty("suppressed", arrayContaining(sameInstance(e1)))));
        control.verify();
    }
}
