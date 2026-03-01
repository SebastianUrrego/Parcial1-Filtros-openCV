cmake_minimum_required(VERSION 3.22.1)

project("opencv_parcial")

# Ruta real de tu OpenCV
set(OpenCV_DIR "C:/Users/braya/Documents/Vision por computador/opencv-4.12.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")

find_package(OpenCV REQUIRED)

add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${OpenCV_INCLUDE_DIRS})

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        ${OpenCV_LIBS})