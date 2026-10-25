package com.restonic4.bloom.core.math;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformTest {
    // Every public way of changing a transform. A new mutator that forgets the frozen check fails here only if it is added to the list
    private static final List<Consumer<Transform>> MUTATIONS = List.of(
            t -> t.setPosition(1, 2, 3),
            t -> t.translate(1, 0, 0),
            t -> t.setScale(2, 2, 2),
            t -> t.scale(2),
            t -> t.scale(1, 2, 3),
            t -> t.setRotation(new Quaternionf()),
            t -> t.setRotation(0, 0, 0, 1),
            t -> t.setRotationXYZRadians(1, 0, 0),
            t -> t.setRotationAxisRadians(1, 0, 1, 0),
            t -> t.setRotationXYZDegrees(90, 0, 0),
            t -> t.setRotationAxisDegrees(90, 0, 1, 0),
            t -> t.rotateXRadians(1),
            t -> t.rotateYRadians(1),
            t -> t.rotateZRadians(1),
            t -> t.rotateXYZRadians(1, 1, 1),
            t -> t.rotateXDegrees(10),
            t -> t.rotateYDegrees(10),
            t -> t.rotateZDegrees(10),
            t -> t.rotateXYZDegrees(10, 10, 10),
            t -> t.rotate(new Quaternionf().rotateX(1)),
            t -> t.identity(),
            t -> t.markDirty()
    );

    // Compared through the getters: the cached matrix would not show a change made before the rejection
    private static List<Float> state(Transform transform) {
        Quaternionf rotation = transform.getRotation(new Quaternionf());

        return List.of(
                transform.getX(), transform.getY(), transform.getZ(),
                transform.getScaleX(), transform.getScaleY(), transform.getScaleZ(),
                rotation.x, rotation.y, rotation.z, rotation.w
        );
    }

    @Test
    void frozenTransformRejectsEveryMutation() {
        for (int i = 0; i < MUTATIONS.size(); i++) {
            Transform transform = new Transform().setPosition(5, 5, 5).setRotationXYZRadians(0.3f, 0.2f, 0.1f).freeze();
            Consumer<Transform> mutation = MUTATIONS.get(i);
            List<Float> before = state(transform);

            assertThrows(IllegalStateException.class, () -> mutation.accept(transform), "mutation #" + i);
            assertEquals(before, state(transform), "mutation #" + i + " changed the transform before being rejected");
        }
    }

    @Test
    void rejectedMutationLeavesTheTransformUntouched() {
        Transform transform = new Transform().setPosition(5, 6, 7).freeze();
        long version = transform.getVersion();

        assertThrows(IllegalStateException.class, () -> transform.setPosition(1, 1, 1));

        assertEquals(5, transform.getX());
        assertEquals(6, transform.getY());
        assertEquals(7, transform.getZ());
        assertEquals(version, transform.getVersion());
    }

    @Test
    void frozenTransformStillReadsAndProducesItsMatrix() {
        Transform transform = new Transform().setPosition(1, 2, 3).freeze();

        assertTrue(transform.isFrozen());
        assertEquals(1, transform.getModelMatrix().m30());
        assertEquals(2, transform.getModelMatrix().m31());
        assertEquals(3, transform.getModelMatrix().m32());
    }

    @Test
    void freezeBeforeFirstMatrixReadStillBuildsTheMatrix() {
        Transform transform = new Transform().setScale(2, 2, 2).freeze();

        assertTrue(transform.isDirty());
        assertEquals(2, transform.getModelMatrix().m00());
        assertFalse(transform.isDirty());
    }

    @Test
    void freezeReturnsTheSameTransformAndIsRepeatable() {
        Transform transform = new Transform();

        assertSame(transform, transform.freeze());
        assertSame(transform, transform.freeze());
    }

    @Test
    void unfrozenTransformStaysMutable() {
        Transform transform = new Transform();

        assertFalse(transform.isFrozen());
        transform.setPosition(1, 2, 3);
        assertEquals(1, transform.getX());
    }
}
