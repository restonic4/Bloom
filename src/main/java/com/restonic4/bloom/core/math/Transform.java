package com.restonic4.bloom.core.math;

import org.joml.*;

public class Transform {
    private float x, y, z;
    private float scaleX, scaleY, scaleZ;
    private final Quaternionf rotation = new Quaternionf();

    private final Matrix4f modelMatrix = new Matrix4f();

    private boolean dirty = true;
    private long version = 0;

    public Transform() { identity(); }

    public Transform(Vector3fc position, Quaternionfc rotation) {
        identity();
        setPosition(position);
        setRotation(rotation);
    }

    public Transform(Vector3fc position, Quaternionfc rotation, Vector3fc scale) {
        setPosition(position);
        setRotation(rotation);
        setScale(scale);
    }

    // Position

    public Transform setPosition(float x, float y, float z) {
        if (this.x != x || this.y != y || this.z != z) {
            this.x = x;
            this.y = y;
            this.z = z;
            markDirty();
        }

        return this;
    }

    public Transform setPosition(Vector3fc position) {
        return setPosition(position.x(), position.y(), position.z());
    }

    public Transform translate(float dx, float dy, float dz) {
        if (dx != 0.0f || dy != 0.0f || dz != 0.0f) {
            x += dx;
            y += dy;
            z += dz;
            markDirty();
        }

        return this;
    }

    // Scale

    public Transform setScale(float scale) {
        return setScale(scale, scale, scale);
    }

    public Transform setScale(float x, float y, float z) {
        if (scaleX != x || scaleY != y || scaleZ != z) {
            scaleX = x;
            scaleY = y;
            scaleZ = z;
            markDirty();
        }

        return this;
    }

    public Transform setScale(Vector3fc scale) {
        return setScale(scale.x(), scale.y(), scale.z());
    }

    public Transform scale(float factor) {
        if (factor != 1.0f) {
            scaleX *= factor;
            scaleY *= factor;
            scaleZ *= factor;
            markDirty();
        }

        return this;
    }

    public Transform scale(float x, float y, float z) {
        if (x != 1.0f || y != 1.0f || z != 1.0f) {
            scaleX *= x;
            scaleY *= y;
            scaleZ *= z;
            markDirty();
        }

        return this;
    }

    // Rotation

    public Transform setRotation(Quaternionfc quaternion) {
        if (!rotation.equals(quaternion)) {
            rotation.set(quaternion);
            markDirty();
        }

        return this;
    }

    public Transform setRotation(float x, float y, float z, float w) {
        if (rotation.x() != x || rotation.y() != y || rotation.z() != z || rotation.w() != w) {
            rotation.set(x, y, z, w);
            markDirty();
        }

        return this;
    }

    public Transform setRotationXYZ(float angleX, float angleY, float angleZ) {
        rotation.rotationXYZ(angleX, angleY, angleZ);
        markDirty();
        return this;
    }

    public Transform setRotationAxis(float angle, float axisX, float axisY, float axisZ) {
        rotation.rotationAxis(angle, axisX, axisY, axisZ);
        markDirty();
        return this;
    }

    public Transform rotateX(float angle) {
        if (angle != 0.0f) {
            rotation.rotateX(angle);
            markDirty();
        }

        return this;
    }

    public Transform rotateY(float angle) {
        if (angle != 0.0f) {
            rotation.rotateY(angle);
            markDirty();
        }

        return this;
    }

    public Transform rotateZ(float angle) {
        if (angle != 0.0f) {
            rotation.rotateZ(angle);
            markDirty();
        }

        return this;
    }

    public Transform rotateXYZ(float angleX, float angleY, float angleZ) {
        if (angleX != 0.0f || angleY != 0.0f || angleZ != 0.0f) {
            rotation.rotateXYZ(angleX, angleY, angleZ);
            markDirty();
        }

        return this;
    }

    public Transform rotate(Quaternionfc delta) {
        rotation.mul(delta);
        markDirty();
        return this;
    }

    // Util

    public Transform identity() {
        boolean changed = x != 0.0f || y != 0.0f || z != 0.0f
                        || scaleX != 1.0f || scaleY != 1.0f || scaleZ != 1.0f
                        || rotation.x() != 0.0f || rotation.y() != 0.0f || rotation.z() != 0.0f || rotation.w() != 1.0f;

        if (changed) {
            x = 0.0f;
            y = 0.0f;
            z = 0.0f;
            scaleX = 1.0f;
            scaleY = 1.0f;
            scaleZ = 1.0f;
            rotation.identity();
            markDirty();
        }

        return this;
    }

    public void markDirty() {
        this.dirty = true;
        if (this.version == Long.MAX_VALUE) throw new ArithmeticException("Transform version overflow!");
        this.version++;
    }

    // Getters

    public Vector3f getPosition(Vector3f dest) { return dest.set(x, y, z); }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }

    public Vector3f getScale(Vector3f dest) { return dest.set(scaleX, scaleY, scaleZ); }
    public float getScaleX() { return scaleX; }
    public float getScaleY() { return scaleY; }
    public float getScaleZ() { return scaleZ; }

    public Quaternionf getRotation(Quaternionf dest) { return dest.set(rotation); }
    public Vector3f getEulerAngles(Vector3f dest) { return rotation.getEulerAnglesXYZ(dest); }

    public Matrix4fc getModelMatrix() {
        if (dirty) {
            modelMatrix.translationRotateScale(
                    x, y, z,
                    rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                    scaleX, scaleY, scaleZ
            );
            dirty = false;
        }

        return modelMatrix;
    }

    public boolean isDirty() { return dirty; }
    public long getVersion() { return version; }
    public boolean isSameVersion(Transform other) { return isSameVersion(other.getVersion()); }
    public boolean isSameVersion(long version) { return this.version == version; }
}
