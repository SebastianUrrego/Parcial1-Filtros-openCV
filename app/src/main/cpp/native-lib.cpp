#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>

using namespace cv;

extern "C"
JNIEXPORT void JNICALL
Java_com_bryan_1lunay_opencv_1parcial_MainActivity_processImageNative(JNIEnv *env, jobject thiz, jlong mat_addr_rgba, jint effect_mode) {
    // Obtenemos la matriz de imagen desde Java
    Mat& rgba = *(Mat*)mat_addr_rgba;

    if (effect_mode == 1) {
        // EJEMPLO 1: Efecto Sketch (Bordes invertidos)
        Mat gray, edges;
        // 1. Convertir a escala de grises
        cvtColor(rgba, gray, COLOR_RGBA2GRAY);
        // 2. Detectar bordes con Canny
        Canny(gray, edges, 50, 150);
        // 3. Invertir colores (bordes negros, fondo blanco)
        bitwise_not(edges, edges);
        // 4. Devolver a formato RGBA para que Android lo pinte
        cvtColor(edges, rgba, COLOR_GRAY2RGBA);

    } else if (effect_mode == 2) {
        // EJEMPLO 2: Efecto Filtro Cálido / Sepia
        Mat sepia;
        // Matriz de transformación de color para Sepia
        Mat kernel = (Mat_<float>(4,4) <<
                                       0.272, 0.534, 0.131, 0,
                0.349, 0.686, 0.168, 0,
                0.393, 0.769, 0.189, 0,
                0,     0,     0,     1);
        transform(rgba, sepia, kernel);
        sepia.copyTo(rgba);
    }
    // Si effect_mode == 0, se mantiene la imagen original.

    else if (effect_mode == 3) {
    // EJEMPLO 3: Segmentación de color verde
    Mat hsv, mask, result;

    // 1. Convertir de RGBA a HSV
    cvtColor(rgba, hsv, COLOR_RGBA2BGR);
    cvtColor(hsv, hsv, COLOR_BGR2HSV);

    // 2. Definir rango de color verde en HSV
    Scalar verde_bajo(35, 50, 50);   // Límite inferior del verde
    Scalar verde_alto(85, 255, 255); // Límite superior del verde

    // 3. Crear máscara: solo los píxeles verdes quedan en blanco
    inRange(hsv, verde_bajo, verde_alto, mask);

    // 4. Aplicar máscara: mostrar solo los píxeles verdes
    result = Mat::zeros(rgba.size(), rgba.type());
    rgba.copyTo(result, mask);
    result.copyTo(rgba);
    }
}