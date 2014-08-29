package org.onlab.graph;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import org.junit.Test;

import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test of the default path.
 */
public class DefaultPathTest extends GraphTest {

    @Test
    public void equality() {
        List<TestEdge> edges = of(new TestEdge(A, B, 1), new TestEdge(B, C, 1));
        new EqualsTester().addEqualityGroup(new DefaultPath<>(edges, 2.0),
                                            new DefaultPath<>(edges, 2.0))
                .addEqualityGroup(new DefaultPath<>(edges, 3.0))
                .testEquals();
    }

    @Test
    public void basics() {
        Path<TestVertex, TestEdge> p = new DefaultPath<>(of(new TestEdge(A, B, 1),
                                                            new TestEdge(B, C, 1)), 2.0);
        validatePath(p, A, C, 2, 2.0);
    }

    // Validates the path against expected attributes
    protected void validatePath(Path<TestVertex, TestEdge> p,
                              TestVertex src, TestVertex dst,
                              int length, double cost) {
        assertEquals("incorrect path length", length, p.edges().size());
        assertEquals("incorrect source", src, p.src());
        assertEquals("incorrect destination", dst, p.dst());
        assertEquals("incorrect path cost", cost, p.cost(), 0.1);
    }

}
