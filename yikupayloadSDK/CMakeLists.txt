cmake_minimum_required(VERSION 3.4.1)

find_library(log-lib log)

# ========== Opus 部分 ==========
set(libs_DIR src/main/jniLibs/${ANDROID_ABI})
set(libs_include_DIR src/main/cpp/opus/include)
include_directories(${libs_include_DIR})
link_directories(${libs_DIR})

add_library(opusJni SHARED src/main/cpp/opus-lib.cpp)
target_link_libraries(opusJni opus ${log-lib})

# ========== AECM 部分 ==========
set(aecm_SRC_DIR src/main/cpp/aecm)

# GLOB_RECURSE 递归匹配所有子目录下的 .c 文件
file(GLOB_RECURSE AECM_SRC
        ${aecm_SRC_DIR}/*.c
        ${aecm_SRC_DIR}/*.cc
)

# 过滤掉不需要的平台专属优化文件（默认排除 NEON 和 MIPS）[1](@ref)
list(FILTER AECM_SRC EXCLUDE REGEX ".*aecm_core_neon.cc$")
list(FILTER AECM_SRC EXCLUDE REGEX ".*aecm_core_mips.cc$")

# 如果你的项目开启了 NEON 支持（比如 armeabi-v7a 开启了 -mfpu=neon），
# 并且你想使用 NEON 加速，可以注释掉上面排除 NEON 的那行，并加上宏定义：
# if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
#     add_definitions(-DWEBRTC_HAS_NEON)
# endif()

add_library(aecm SHARED
        ${AECM_SRC}
        src/main/cpp/aecm_wrapper.c    # 你的 JNI 胶水层
)

# AECM 头文件路径
target_include_directories(aecm PRIVATE
        ${aecm_SRC_DIR}
)

# 添加 C++11 标准支持（WebRTC 的 .cc 文件需要）[2](@ref)
set_target_properties(aecm PROPERTIES CXX_STANDARD 11)

target_compile_options(aecm PRIVATE
        -O2
        -Wall
        -Wno-unused-variable
        -Wno-implicit-function-declaration
        -Wno-unused-function
)

# 链接日志库和数学库(m)[2](@ref)
target_link_libraries(aecm ${log-lib} m)