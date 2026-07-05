# Builds llama.cpp (core only) statically into one libvocatim_llm.so.
#
# Static linking + hidden visibility is deliberate: the :whisper module ships
# its own SHARED libggml.so. If this module also produced shared ggml, the two
# different ggml versions would collide in the APK. Linking ggml statically
# with hidden symbols keeps them isolated.
cmake_minimum_required(VERSION 3.22)
project(vocatim_llm CXX C)

set(CMAKE_CXX_STANDARD 17)
set(LLAMA_DIR ${CMAKE_CURRENT_LIST_DIR}/../llama.cpp)

# Static everything; no dynamic backend loading, no extras.
set(BUILD_SHARED_LIBS       OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL         OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP             OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON      OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL              OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} ${CMAKE_BINARY_DIR}/llama-build)

find_library(LOG_LIB log)

add_library(vocatim_llm SHARED llm_jni.cpp)

target_include_directories(vocatim_llm PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include)

target_compile_options(vocatim_llm PRIVATE
    -O3 -fvisibility=hidden -fvisibility-inlines-hidden
    -ffunction-sections -fdata-sections)

target_link_libraries(vocatim_llm llama ${LOG_LIB})

# Don't re-export llama/ggml symbols; drop unused code; strip symbols
# (AGP fails to strip this large lib, so do it at link time -> ~45MB to ~10MB).
target_link_options(vocatim_llm PRIVATE
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,-s)
