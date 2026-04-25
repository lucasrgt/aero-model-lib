package aero.modellib;

final class Aero_BoneRenderPose {
    float pivotX;
    float pivotY;
    float pivotZ;
    float rotX;
    float rotY;
    float rotZ;
    float offsetX;
    float offsetY;
    float offsetZ;
    float scaleX;
    float scaleY;
    float scaleZ;

    void reset() {
        pivotX = 0f;
        pivotY = 0f;
        pivotZ = 0f;
        rotX = 0f;
        rotY = 0f;
        rotZ = 0f;
        offsetX = 0f;
        offsetY = 0f;
        offsetZ = 0f;
        scaleX = 1f;
        scaleY = 1f;
        scaleZ = 1f;
    }

    void setPivot(float[] pivot) {
        pivotX = pivot[0];
        pivotY = pivot[1];
        pivotZ = pivot[2];
    }
}
