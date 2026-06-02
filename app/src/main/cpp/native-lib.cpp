#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <algorithm>
#include <random>

const int WORLD_SIZE_X = 24;
const int WORLD_SIZE_Y = 24;
const int WORLD_SIZE_Z = 24;

// Use uint8_t to store blocks to make memory footprint extremely light (24x24x24 = ~13.8 KB)
uint8_t world[WORLD_SIZE_X][WORLD_SIZE_Y][WORLD_SIZE_Z] = {0};

struct Voxel {
    uint8_t x, y, z, type;
    float depth;
};

extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeVoxelEngine_initWorld(JNIEnv *env, jobject thiz, jint seed, jint preset) {
    // Clear first
    for (int x = 0; x < WORLD_SIZE_X; ++x) {
        for (int y = 0; y < WORLD_SIZE_Y; ++y) {
            for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                world[x][y][z] = 0;
            }
        }
    }
    
    switch (preset) {
        case 0: { // Rolling Hills
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                    float noise = sin(x * 0.2f + seed) * cos(z * 0.2f + seed * 1.5f) * 3.0f;
                    int height = (int)(WORLD_SIZE_Y / 2.0f + noise);
                    
                    if (height < 1) height = 1;
                    if (height >= WORLD_SIZE_Y) height = WORLD_SIZE_Y - 1;
                    
                    for (int y = 0; y < height; ++y) {
                        if (y == height - 1) {
                            world[x][y][z] = 1; // Grass
                        } else if (y >= height - 3) {
                            world[x][y][z] = 2; // Dirt
                        } else {
                            world[x][y][z] = 3; // Stone
                        }
                    }
                }
            }
            break;
        }
        case 1: { // Floating Islands
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int y = 0; y < WORLD_SIZE_Y; ++y) {
                    for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                        float val = sin(x * 0.35f + seed) * cos(y * 0.4f) * sin(z * 0.35f) 
                                  + cos(x * 0.2f) * sin(y * 0.2f) * cos(z * 0.2f);
                        if (val > 0.25f && y >= 4 && y <= WORLD_SIZE_Y - 4) {
                            if (val > 0.45f) {
                                world[x][y][z] = 8; // Neon Glow Orange
                            } else {
                                world[x][y][z] = 10; // Brick or Glass
                            }
                        }
                    }
                }
            }
            // Bedrock stone layer
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                    world[x][0][z] = 3; // Stone base
                }
            }
            break;
        }
        case 2: { // Castle Defense Fortress
            // Floor
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                    world[x][0][z] = 1; // Grass
                }
            }
            
            // Fortress bounds
            int minCoord = 3;
            int maxCoord = WORLD_SIZE_X - 4;
            int wallHeight = 5;
            
            for (int x = minCoord; x <= maxCoord; ++x) {
                for (int y = 1; y <= wallHeight; ++y) {
                    world[x][y][minCoord] = 6; // Brick Walls
                    world[x][y][maxCoord] = 6;
                }
            }
            for (int z = minCoord; z <= maxCoord; ++z) {
                for (int y = 1; y <= wallHeight; ++y) {
                    world[minCoord][y][z] = 6;
                    world[maxCoord][y][z] = 6;
                }
            }
            
            // Clear gateway
            for (int y = 1; y <= 3; ++y) {
                world[WORLD_SIZE_X / 2][y][minCoord] = 0;
            }
            
            // Pillars / Towers
            int pillars[4][2] = {
                {minCoord, minCoord},
                {minCoord, maxCoord},
                {maxCoord, minCoord},
                {maxCoord, maxCoord}
            };
            for (auto p : pillars) {
                int px = p[0];
                int pz = p[1];
                for (int y = 1; y <= wallHeight + 3; ++y) {
                    world[px][y][pz] = 3; // Stone core
                    if (y == wallHeight + 3) {
                        world[px][y][pz] = 9; // Neon Cyan topping
                    }
                }
            }
            break;
        }
        case 3: { // Deep Caves / Symmetrical Arches
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int y = 0; y < WORLD_SIZE_Y; ++y) {
                    for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                        float cx = (x - WORLD_SIZE_X / 2.0f);
                        float cy = (y - WORLD_SIZE_Y / 2.0f);
                        float cz = (z - WORLD_SIZE_Z / 2.0f);
                        float dist = sqrt(cx * cx + cy * cy + cz * cz);
                        
                        // Solid core with cavern inside
                        if (dist > 9.0f) {
                            world[x][y][z] = 3; // Stone outside outer cave
                        } else if (dist < 6.0f) {
                            world[x][y][z] = 0; // Empty room inside
                        } else if (y < 4) {
                            world[x][y][z] = 2; // Bottom cave dirt floor
                        } else {
                            // Arches/pillars supporting the inside
                            if (((x % 4 == 0) && (z % 4 == 0))) {
                                world[x][y][z] = 6; // Brick column support
                            }
                        }
                    }
                }
            }
            break;
        }
        case 4: { // Golden Ruins / Aztec Pyramid
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                    world[x][0][z] = 1; // Grass base layer
                }
            }
            
            int levels = 7;
            for (int r = 0; r < levels; ++r) {
                int start = r + 1;
                int end = WORLD_SIZE_X - 2 - r;
                int y = r + 1;
                for (int x = start; x <= end; ++x) {
                    for (int z = start; z <= end; ++z) {
                        // Blend ruins material: brick & gold blocks
                        if ((x + z + y) % 3 == 0) {
                            world[x][y][z] = 7; // Gold block
                        } else {
                            world[x][y][z] = 6; // Brick block
                        }
                    }
                }
            }
            break;
        }
        default: { // Flat Grass Land
            for (int x = 0; x < WORLD_SIZE_X; ++x) {
                for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                    world[x][0][z] = 1; // Grass floor
                }
            }
            break;
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeVoxelEngine_setBlock(JNIEnv *env, jobject thiz, jint x, jint y, jint z, jbyte type) {
    if (x >= 0 && x < WORLD_SIZE_X && y >= 0 && y < WORLD_SIZE_Y && z >= 0 && z < WORLD_SIZE_Z) {
        world[x][y][z] = type;
    }
}

extern "C" JNIEXPORT jbyte JNICALL
Java_com_example_NativeVoxelEngine_getBlock(JNIEnv *env, jobject thiz, jint x, jint y, jint z) {
    if (x >= 0 && x < WORLD_SIZE_X && y >= 0 && y < WORLD_SIZE_Y && z >= 0 && z < WORLD_SIZE_Z) {
        return world[x][y][z];
    }
    return 0;
}

// Memory-optimized fast painter's sort
extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_NativeVoxelEngine_getSortedBlocks(JNIEnv *env, jobject thiz, jfloat camX, jfloat camY, jfloat camZ) {
    std::vector<Voxel> voxels;
    voxels.reserve(4096); // Warm allocations
    
    for (int x = 0; x < WORLD_SIZE_X; ++x) {
        for (int y = 0; y < WORLD_SIZE_Y; ++y) {
            for (int z = 0; z < WORLD_SIZE_Z; ++z) {
                uint8_t type = world[x][y][z];
                if (type > 0) {
                    float dx = (x + 0.5f) - camX;
                    float dy = (y + 0.5f) - camY;
                    float dz = (z + 0.5f) - camZ;
                    float distSq = dx * dx + dy * dy + dz * dz;
                    voxels.push_back({(uint8_t)x, (uint8_t)y, (uint8_t)z, type, distSq});
                }
            }
        }
    }

    // Sort by distance from camera in DESCENDING order (farthest first)
    std::sort(voxels.begin(), voxels.end(), [](const Voxel& a, const Voxel& b) {
        return a.depth > b.depth;
    });

    int count = voxels.size();
    jintArray result = env->NewIntArray(count);
    if (count > 0) {
        std::vector<jint> tempBuffer(count);
        for (int i = 0; i < count; ++i) {
            jint packed = (voxels[i].x & 0xFF) | 
                          ((voxels[i].y & 0xFF) << 8) | 
                          ((voxels[i].z & 0xFF) << 16) | 
                          ((voxels[i].type & 0xFF) << 24);
            tempBuffer[i] = packed;
        }
        env->SetIntArrayRegion(result, 0, count, tempBuffer.data());
    }
    return result;
}

// DDA precise raycasting engine optimized in C++
extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_NativeVoxelEngine_raycast(JNIEnv *env, jobject thiz, 
                                          jfloat startX, jfloat startY, jfloat startZ, 
                                          jfloat dirX, jfloat dirY, jfloat dirZ, 
                                          jfloat maxDist) {
    jintArray result = env->NewIntArray(7);
    jint hitData[7] = {-1, -1, -1, 0, 0, 0, 0}; // [x, y, z, faceX, faceY, faceZ, isHit]
    
    // Normalize directions safely
    float len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
    if (len > 0.0001f) {
        dirX /= len;
        dirY /= len;
        dirZ /= len;
    }
    
    float step = 0.05f;
    float currentDist = 0.0f;
    int lastX = -1, lastY = -1, lastZ = -1;
    
    while (currentDist <= maxDist) {
        float px = startX + dirX * currentDist;
        float py = startY + dirY * currentDist;
        float pz = startZ + dirZ * currentDist;
        
        int ix = (int)floor(px);
        int iy = (int)floor(py);
        int iz = (int)floor(pz);
        
        if (ix >= 0 && ix < WORLD_SIZE_X && iy >= 0 && iy < WORLD_SIZE_Y && iz >= 0 && iz < WORLD_SIZE_Z) {
            uint8_t type = world[ix][iy][iz];
            if (type > 0) { // Solid block found
                hitData[0] = ix;
                hitData[1] = iy;
                hitData[2] = iz;
                hitData[6] = 1; // Mark as Hit
                
                // Set the exact offset face normal
                if (lastX >= 0 && (lastX != ix || lastY != iy || lastZ != iz)) {
                    hitData[3] = lastX - ix;
                    hitData[4] = lastY - iy;
                    hitData[5] = lastZ - iz;
                }
                break;
            }
        }
        
        lastX = ix;
        lastY = iy;
        lastZ = iz;
        currentDist += step;
    }
    
    env->SetIntArrayRegion(result, 0, 7, hitData);
    return result;
}

// ----------------------------------------------------
// CLASSIC WOOD BLOCK PUZZLE JNI ENGINE
// ----------------------------------------------------

uint8_t puzzle_board[8][8] = {0};

extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeVoxelEngine_initPuzzleGame(JNIEnv *env, jobject thiz) {
    for (int r = 0; r < 8; ++r) {
        for (int c = 0; c < 8; ++c) {
            puzzle_board[r][c] = 0;
        }
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NativeVoxelEngine_getPuzzleBoardCell(JNIEnv *env, jobject thiz, jint r, jint c) {
    if (r >= 0 && r < 8 && c >= 0 && c < 8) {
        return puzzle_board[r][c];
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_NativeVoxelEngine_setPuzzleBoardCell(JNIEnv *env, jobject thiz, jint r, jint c, jint val) {
    if (r >= 0 && r < 8 && c >= 0 && c < 8) {
        puzzle_board[r][c] = (uint8_t)val;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_NativeVoxelEngine_canPlaceShapeNative(JNIEnv *env, jobject thiz, jintArray shapeCoords, jint r, jint c) {
    jsize len = env->GetArrayLength(shapeCoords);
    if (len % 2 != 0) return JNI_FALSE;
    
    jint* coords = env->GetIntArrayElements(shapeCoords, nullptr);
    bool possible = true;
    
    for (int i = 0; i < len; i += 2) {
        int pr = r + coords[i];
        int pc = c + coords[i + 1];
        if (pr < 0 || pr >= 8 || pc < 0 || pc >= 8 || puzzle_board[pr][pc] != 0) {
            possible = false;
            break;
        }
    }
    
    env->ReleaseIntArrayElements(shapeCoords, coords, JNI_ABORT);
    return possible ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_NativeVoxelEngine_placeShapeNative(JNIEnv *env, jobject thiz, jintArray shapeCoords, jint r, jint c, jint cellValue) {
    jsize len = env->GetArrayLength(shapeCoords);
    if (len % 2 != 0) return JNI_FALSE;
    
    jint* coords = env->GetIntArrayElements(shapeCoords, nullptr);
    
    // First, check if everything is valid
    bool possible = true;
    for (int i = 0; i < len; i += 2) {
        int pr = r + coords[i];
        int pc = c + coords[i + 1];
        if (pr < 0 || pr >= 8 || pc < 0 || pc >= 8 || puzzle_board[pr][pc] != 0) {
            possible = false;
            break;
        }
    }
    
    if (possible) {
        for (int i = 0; i < len; i += 2) {
            int pr = r + coords[i];
            int pc = c + coords[i + 1];
            puzzle_board[pr][pc] = (uint8_t)cellValue;
        }
    }
    
    env->ReleaseIntArrayElements(shapeCoords, coords, JNI_ABORT);
    return possible ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_NativeVoxelEngine_clearLinesNative(JNIEnv *env, jobject thiz) {
    bool fullRows[8] = {false};
    bool fullCols[8] = {false};
    int clearedCount = 0;
    
    // Check rows
    for (int r = 0; r < 8; ++r) {
        bool rowFull = true;
        for (int c = 0; c < 8; ++c) {
            if (puzzle_board[r][c] == 0) {
                rowFull = false;
                break;
            }
        }
        if (rowFull) {
            fullRows[r] = true;
            clearedCount++;
        }
    }
    
    // Check columns
    for (int c = 0; c < 8; ++c) {
        bool colFull = true;
        for (int r = 0; r < 8; ++r) {
            if (puzzle_board[r][c] == 0) {
                colFull = false;
                break;
            }
        }
        if (colFull) {
            fullCols[c] = true;
            clearedCount++;
        }
    }
    
    // Perform clear
    int mask = 0;
    for (int r = 0; r < 8; ++r) {
        if (fullRows[r]) {
            mask |= (1 << r);
            for (int c = 0; c < 8; ++c) {
                puzzle_board[r][c] = 0;
            }
        }
    }
    for (int c = 0; c < 8; ++c) {
        if (fullCols[c]) {
            mask |= (1 << (c + 8));
            for (int r = 0; r < 8; ++r) {
                puzzle_board[r][c] = 0;
            }
        }
    }
    
    // Return mask packed with cleared count:
    // Bits 0-7: row mask
    // Bits 8-15: col mask
    // Bits 16-23: clearedLine count
    return mask | (clearedCount << 16);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_NativeVoxelEngine_checkShapeFitsAnywhere(JNIEnv *env, jobject thiz, jintArray shapeCoords) {
    jsize len = env->GetArrayLength(shapeCoords);
    if (len % 2 != 0) return JNI_FALSE;
    
    jint* coords = env->GetIntArrayElements(shapeCoords, nullptr);
    bool canFitOverall = false;
    
    for (int r = 0; r < 8; ++r) {
        for (int c = 0; c < 8; ++c) {
            bool fitsAtThisPosition = true;
            for (int i = 0; i < len; i += 2) {
                int pr = r + coords[i];
                int pc = c + coords[i + 1];
                if (pr < 0 || pr >= 8 || pc < 0 || pc >= 8 || puzzle_board[pr][pc] != 0) {
                    fitsAtThisPosition = false;
                    break;
                }
            }
            if (fitsAtThisPosition) {
                canFitOverall = true;
                goto end_search;
            }
        }
    }
    
end_search:
    env->ReleaseIntArrayElements(shapeCoords, coords, JNI_ABORT);
    return canFitOverall ? JNI_TRUE : JNI_FALSE;
}
