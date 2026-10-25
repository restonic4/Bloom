package com.restonic4.bloom.core.scene;

import com.restonic4.bloom.core.math.Transform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Camera {
    public enum ProjectionType {
        PERSPECTIVE,
        ORTHOGRAPHIC
    }

    private static final float MIN_VALUE = 0.00001f;
    private static final float MIN_FOV = MIN_VALUE;
    private static final float MIN_PLANE = MIN_VALUE;
    private static final float MIN_ORTHOGRAPHIC_HEIGHT = MIN_VALUE;
    private static final float MIN_PLANE_GAP = MIN_VALUE;
    private static final float MIN_ASPECT_RATIO = MIN_VALUE;

    private static final float MAX_FOV = (float) Math.PI - MIN_FOV;

    private final Transform transform = new Transform();
    private ProjectionType projectionType = ProjectionType.PERSPECTIVE;

    // Perspective settings
    private float fieldOfView = (float) Math.toRadians(70.0);
    private float nearPlane = 0.1f;
    private float farPlane = 1000.0f;

    // Orthographic setting
    private float orthographicHeight = 10.0f;

    private long lastTransformVersion = -1;
    private float lastAspectRatio = -1.0f;
    private boolean projectionDirty = true;

    private final Vector3f inversePosition = new Vector3f();
    private final Quaternionf inverseRotation = new Quaternionf();

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    public Camera() {}

    // Projection

    public void setProjectionType(ProjectionType projectionType) {
        if (this.projectionType != projectionType) {
            this.projectionType = projectionType;
            projectionDirty = true;
        }
    }

    public void setFieldOfViewRadians(float fieldOfView) {
        fieldOfView = Math.clamp(fieldOfView, MIN_FOV, MAX_FOV);

        if (this.fieldOfView != fieldOfView) {
            this.fieldOfView = fieldOfView;
            projectionDirty = true;
        }
    }

    public void setFieldOfViewDegrees(float fieldOfView) {
        setFieldOfViewRadians((float) Math.toRadians(fieldOfView));
    }

    public void setNearPlane(float nearPlane) {
        nearPlane = Math.max(nearPlane, MIN_PLANE);

        if (this.nearPlane != nearPlane) {
            this.nearPlane = nearPlane;
            projectionDirty = true;
        }
    }

    public void setFarPlane(float farPlane) {
        farPlane = Math.max(farPlane, nearPlane + MIN_PLANE_GAP);

        if (this.farPlane != farPlane) {
            this.farPlane = farPlane;
            projectionDirty = true;
        }
    }

    public void setOrthographicHeight(float orthographicHeight) {
        orthographicHeight = Math.max(orthographicHeight, MIN_ORTHOGRAPHIC_HEIGHT);

        if (this.orthographicHeight != orthographicHeight) {
            this.orthographicHeight = orthographicHeight;
            projectionDirty = true;
        }
    }

    // View matrix

    public Matrix4fc getViewMatrix() {
        long transformVersion = transform.getVersion();

        if (lastTransformVersion != transformVersion) {
            updateViewMatrix();
            lastTransformVersion = transformVersion;
        }

        return viewMatrix;
    }

    private void updateViewMatrix() {
        transform.getPosition(inversePosition).negate();
        transform.getRotation(inverseRotation).conjugate();
        viewMatrix.identity().rotate(inverseRotation).translate(inversePosition);
    }

    // Projection matrix

    public Matrix4fc getProjectionMatrix(float aspectRatio) {
        aspectRatio = Math.max(aspectRatio, MIN_ASPECT_RATIO);

        if (projectionDirty || lastAspectRatio != aspectRatio) {
            updateProjectionMatrix(aspectRatio);
        }

        return projectionMatrix;
    }

    private void updateProjectionMatrix(float aspectRatio) {
        if (projectionType == ProjectionType.PERSPECTIVE) {
            projectionMatrix.identity().perspective(fieldOfView, aspectRatio, nearPlane, farPlane);
        } else {
            float width = orthographicHeight * aspectRatio;
            projectionMatrix.identity().orthoSymmetric(width, orthographicHeight, nearPlane, farPlane);
        }

        lastAspectRatio = aspectRatio;
        projectionDirty = false;
    }

    // Getters

    public Transform getTransform() { return transform; }
    public ProjectionType getProjectionType() { return projectionType; }

    public float getNearPlane() { return nearPlane; }
    public float getFarPlane() { return farPlane; }

    public float getFieldOfViewRadians() { return fieldOfView; }
    public float getFieldOfViewDegrees() { return (float) Math.toDegrees(fieldOfView); }
    public float getOrthographicHeight() { return orthographicHeight; }

    public boolean isDirty() { return projectionDirty || !transform.isSameVersion(lastTransformVersion); }
    public boolean isProjectionDirty() { return projectionDirty; }
}
