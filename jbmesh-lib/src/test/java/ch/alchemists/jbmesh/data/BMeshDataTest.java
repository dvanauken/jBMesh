package ch.alchemists.jbmesh.data;

import ch.alchemists.jbmesh.data.property.FloatProperty;
import ch.alchemists.jbmesh.data.property.IntTupleProperty;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class BMeshDataTest {
    private static class TestElement extends Element {
        @Override
        protected void releaseElement() {}
    }


    @Test
    public void testElementReference() {
        BMeshData<TestElement> data = new BMeshData<>(TestElement::new);

        TestElement e1 = data.create();
        data.destroy(e1);
        TestElement e2 = data.create();

        assertFalse(e1.isAlive());
        assertNotEquals(e1, e2);

        data.destroy(e1);
        assertTrue(e2.isAlive());
    }
    

    @Test
    public void testPropertyAddRemove() {
        final String propName = "Prop";

        BMeshData<TestElement> data = new BMeshData<>(TestElement::new);
        assertNull(data.getProperty(propName));

        FloatProperty<TestElement> prop = new FloatProperty<>(propName);
        assertNull(prop.data);

        data.addProperty(prop);
        assertEquals(prop, data.getProperty(propName));
        assertNotNull(prop.data);

        assertThrows(IllegalStateException.class, () -> {
            data.addProperty(prop);
        });

        data.removeProperty(prop);
        assertNull(prop.data);
        assertNull(data.getProperty(propName));

        data.addProperty(prop);
        assertNotNull(prop.data);
    }


    @Test
    public void testCompact() {
        BMeshData<TestElement> data = new BMeshData<>(TestElement::new);
        IntTupleProperty<TestElement> prop = new IntTupleProperty<>("Prop", 3);
        data.addProperty(prop);

        TestElement[] elements = new TestElement[13];
        for(int i=0; i<elements.length; ++i) {
            elements[i] = data.create();
            prop.setValues(elements[i], i, i, i);
        }

        assertEquals(13, data.size());

        data.destroy(elements[0]);
        // 1
        // 2
        data.destroy(elements[3]);
        // 4
        data.destroy(elements[5]);
        data.destroy(elements[6]);
        // 7
        // 8
        data.destroy(elements[9]);
        data.destroy(elements[10]);
        data.destroy(elements[11]);
        // 12

        assertEquals(6, data.size());

        data.compactData();

        assertEquals(6, data.size());
        assertEquals(6*3, prop.data.length);

        assertValues(prop, elements);
    }


    /**
     * Tests compacting/copying of values up to the first free slot.
     */
    @Test
    public void testCompactFirstSegment() {
        BMeshData<TestElement> data = new BMeshData<>(TestElement::new);
        IntTupleProperty<TestElement> prop = new IntTupleProperty<>("Prop", 3);
        data.addProperty(prop);

        TestElement[] elements = new TestElement[6];
        for(int i=0; i<elements.length; ++i) {
            elements[i] = data.create();
            prop.setValues(elements[i], i, i, i);
        }

        assertEquals(6, data.size());
        data.destroy(elements[2]);
        assertEquals(5, data.size());
        data.compactData();

        assertValues(prop, elements);
    }


    private void assertValues(IntTupleProperty<TestElement> prop, TestElement[] elements) {
        for(int i=0; i<elements.length; ++i) {
            if(!elements[i].isAlive())
                continue;

            assertEquals(i, prop.get(elements[i], 0));
            assertEquals(i, prop.get(elements[i], 1));
            assertEquals(i, prop.get(elements[i], 2));
        }
    }
}
