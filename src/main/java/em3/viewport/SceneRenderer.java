package em3.viewport;

import em3.animation.RegionMath;
import em3.animation.SpritePlacement;
import em3.app.EditorSession;
import em3.app.Overlays;
import em3.assets.OutlineImages;
import em3.mindustry.MindustryV160;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetAttribLocation;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;

/**
 * Draws evaluated sprites into an framebuffer. Textures use nearest-neighbor filtering.
 * This is not Arc's SpriteBatch. Vertex placement comes from {@code SpritePlacement}, which follows
 * {@code SpriteBatch.constructVertices} with exact trigonometry.
 */
public final class SceneRenderer {
    private int program;
    private int vao;
    private int vbo;
    private int whiteTex;
    private int fbo;
    private int fboTex;
    private int fboW;
    private int fboH;
    private int uProj;
    private final Map<String, Integer> textures = new HashMap<>();
    private final Map<String, Integer> outlineKeys = new HashMap<>();
    private final FloatBuffer matrix = memAllocFloat(16);
    private boolean ready;

    public void init() {
        if (ready) {
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        program = program(VERT, FRAG);
        uProj = glGetUniformLocation(program, "uProj");
        int uTex = glGetUniformLocation(program, "uTex");
        glUseProgram(program);
        glUniform1i(uTex, 0);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int pos = glGetAttribLocation(program, "aPos");
        int uv = glGetAttribLocation(program, "aUv");
        int col = glGetAttribLocation(program, "aColor");
        int stride = 8 * Float.BYTES;
        glEnableVertexAttribArray(pos);
        glVertexAttribPointer(pos, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(uv);
        glVertexAttribPointer(uv, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(col);
        glVertexAttribPointer(col, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glBindVertexArray(0);
        whiteTex = solidTexture();
        ready = true;
    }

    public int textureId() {
        return fboTex;
    }

    public void render(EditorSession session, int width, int height) {
        init();
        int w = Math.max(8, width);
        int h = Math.max(8, height);
        ensureFbo(w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, w, h);
        Overlays overlays = session.overlays;
        if (overlays.transparency) {
            glClearColor(0.55f, 0.22f, 0.55f, 1f);
        } else {
            glClearColor(0.145f, 0.145f, 0.16f, 1f);
        }
        glClear(GL_COLOR_BUFFER_BIT);
        float viewW = w / session.zoom;
        float viewH = h / session.zoom;
        ortho(session.cameraX - viewW / 2f, session.cameraX + viewW / 2f,
                session.cameraY - viewH / 2f, session.cameraY + viewH / 2f);
        glUseProgram(program);
        glUniformMatrix4fv(uProj, false, matrix);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        if (overlays.grid) {
            drawGrid(session, viewW, viewH);
        }
        if (overlays.symmetry) {
            drawSymmetry(session);
        }
        for (SpritePlacement placement : session.placements) {
            if (!visible(placement, overlays)) {
                continue;
            }
            if (placement.missing || Math.abs(placement.width) < 1e-4f || Math.abs(placement.height) < 1e-4f) {
                continue;
            }
            int tex = textureFor(session, placement);
            glBindTexture(GL_TEXTURE_2D, tex);
            if (placement.additive) {
                glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            } else {
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
            drawQuad(placement);
        }
        if (overlays.bounds || overlays.pivots || overlays.paths) {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBindTexture(GL_TEXTURE_2D, whiteTex);
            for (SpritePlacement placement : session.placements) {
                if (!"region".equals(placement.role) && !"base".equals(placement.role)) {
                    continue;
                }
                if (overlays.bounds && "region".equals(placement.role)) {
                    stroke(placement.corners, 0.35f, 0.75f, 1f, 0.9f, 0.06f);
                }
                if (overlays.pivots && ("region".equals(placement.role) || "base".equals(placement.role))) {
                    cross(placement.centerX, placement.centerY, 0.35f, 1f, 0.85f, 0.2f, 1f);
                }
            }
            if (overlays.paths) {
                for (SpritePlacement now : session.placements) {
                    if (!"region".equals(now.role)) {
                        continue;
                    }
                    for (SpritePlacement rest : session.restPlacements) {
                        if (now.selectionKey.equals(rest.selectionKey) && "region".equals(rest.role)) {
                            line(rest.centerX, rest.centerY, now.centerX, now.centerY, 0.02f, 0.4f, 1f, 0.45f, 0.85f);
                        }
                    }
                }
            }
        }
        drawGizmo(session);
        glBindVertexArray(0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private boolean visible(SpritePlacement placement, Overlays overlays) {
        return switch (placement.role) {
            case "base" -> overlays.base;
            case "shadow" -> overlays.base;
            case "outline" -> overlays.outlines;
            case "heat" -> overlays.heat;
            default -> true;
        };
    }

    private int textureFor(EditorSession session, SpritePlacement placement) {
        if ("outline".equals(placement.role)) {
            String source = placement.sprite.endsWith("-outline")
                    ? placement.sprite.substring(0, placement.sprite.length() - "-outline".length())
                    : placement.sprite;
            int key = session.overlays.outlineRadius * 31 + session.overlays.outlineArgb;
            Integer cachedKey = outlineKeys.get(placement.sprite);
            Integer existing = textures.get(placement.sprite);
            if (existing != null && cachedKey != null && cachedKey == key) {
                return existing;
            }
            BufferedImage src = session.sprites.image(source);
            if (src == null) {
                return whiteTex;
            }
            BufferedImage outlined = OutlineImages.outline(src, session.overlays.outlineArgb, session.overlays.outlineRadius);
            int tex = upload(outlined, true);
            if (existing != null) {
                glDeleteTextures(existing);
            }
            textures.put(placement.sprite, tex);
            outlineKeys.put(placement.sprite, key);
            return tex;
        }
        Integer existing = textures.get(placement.sprite);
        if (existing != null) {
            return existing;
        }
        BufferedImage image = session.sprites.image(placement.sprite);
        if (image == null) {
            return whiteTex;
        }
        int tex = upload(image, true);
        textures.put(placement.sprite, tex);
        return tex;
    }

    public void forgetTextures() {
        for (int tex : textures.values()) {
            glDeleteTextures(tex);
        }
        textures.clear();
        outlineKeys.clear();
    }

    private void drawGizmo(EditorSession session) {
        SpritePlacement selected = session.selectedPlacement();
        if (selected == null || !"region".equals(selected.role)) {
            return;
        }
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture(GL_TEXTURE_2D, whiteTex);
        float sign = selected.side == 1 ? -1f : 1f;
        float[] axis = RegionMath.localAxis(session.params.rotation, sign);
        float len = 2.2f;
        line(selected.centerX, selected.centerY,
                selected.centerX + axis[0] * len, selected.centerY + axis[1] * len,
                2.2f / session.zoom, 0.95f, 0.25f, 0.22f, 1f);
        line(selected.centerX, selected.centerY,
                selected.centerX + axis[2] * len, selected.centerY + axis[3] * len,
                2.2f / session.zoom, 0.25f, 0.85f, 0.28f, 1f);
    }

    private void drawGrid(EditorSession session, float viewW, float viewH) {
        glBindTexture(GL_TEXTURE_2D, whiteTex);
        float tile = MindustryV160.TILE_SIZE;
        float left = session.cameraX - viewW / 2f;
        float right = session.cameraX + viewW / 2f;
        float bottom = session.cameraY - viewH / 2f;
        float top = session.cameraY + viewH / 2f;
        int x0 = (int) Math.floor(left / tile) - 1;
        int x1 = (int) Math.ceil(right / tile) + 1;
        int y0 = (int) Math.floor(bottom / tile) - 1;
        int y1 = (int) Math.ceil(top / tile) + 1;
        int limit = 80;
        if (x1 - x0 > limit || y1 - y0 > limit) {
            return;
        }
        float thickness = 1.2f / session.zoom;
        for (int x = x0; x <= x1; x++) {
            float a = x % 2 == 0 ? 0.28f : 0.14f;
            line(x * tile, bottom, x * tile, top, thickness, 0.75f, 0.75f, 0.8f, a);
        }
        for (int y = y0; y <= y1; y++) {
            float a = y % 2 == 0 ? 0.28f : 0.14f;
            line(left, y * tile, right, y * tile, thickness, 0.75f, 0.75f, 0.8f, a);
        }
        float size = session.project.turret.size * tile;
        rectStroke(-size / 2f, -size / 2f, size, size, thickness * 1.6f, 0.95f, 0.78f, 0.35f, 0.7f);
    }

    private void drawSymmetry(EditorSession session) {
        glBindTexture(GL_TEXTURE_2D, whiteTex);
        float rot = (float) Math.toRadians(session.params.rotation - 90f);
        float len = session.project.turret.size * MindustryV160.TILE_SIZE;
        float dx = (float) Math.cos(rot) * len;
        float dy = (float) Math.sin(rot) * len;
        // Mirror axis is local +Y, which is the barrel axis after the same rotation RegionPart applies.
        float ax = -dy;
        float ay = dx;
        float mag = (float) Math.hypot(ax, ay);
        if (mag < 1e-4f) {
            return;
        }
        ax = ax / mag * len;
        ay = ay / mag * len;
        line(-ax, -ay, ax, ay, 1.5f / session.zoom, 0.3f, 0.85f, 1f, 0.75f);
    }

    private void drawQuad(SpritePlacement placement) {
        float[] c = placement.corners;
        float[] uv = {0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f};
        float[] data = new float[6 * 8];
        int[] idx = {0, 1, 2, 0, 2, 3};
        int p = 0;
        for (int k : idx) {
            data[p++] = c[k * 2];
            data[p++] = c[k * 2 + 1];
            data[p++] = uv[k * 2];
            data[p++] = uv[k * 2 + 1];
            data[p++] = placement.r;
            data[p++] = placement.g;
            data[p++] = placement.b;
            data[p++] = placement.a;
        }
        uploadVerts(data);
        org.lwjgl.opengl.GL11.glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void line(float x0, float y0, float x1, float y1, float thickness, float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-5f) {
            return;
        }
        float nx = -dy / len * thickness;
        float ny = dx / len * thickness;
        float[] data = quadData(
                x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny,
                r, g, b, a);
        uploadVerts(data);
        org.lwjgl.opengl.GL11.glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void stroke(float[] corners, float r, float g, float b, float a, float thickness) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            line(corners[i * 2], corners[i * 2 + 1], corners[j * 2], corners[j * 2 + 1], thickness, r, g, b, a);
        }
    }

    private void cross(float x, float y, float size, float r, float g, float b, float a) {
        line(x - size, y, x + size, y, size * 0.12f, r, g, b, a);
        line(x, y - size, x, y + size, size * 0.12f, r, g, b, a);
    }

    private void rectStroke(float x, float y, float w, float h, float t, float r, float g, float b, float a) {
        line(x, y, x + w, y, t, r, g, b, a);
        line(x + w, y, x + w, y + h, t, r, g, b, a);
        line(x + w, y + h, x, y + h, t, r, g, b, a);
        line(x, y + h, x, y, t, r, g, b, a);
    }

    private float[] quadData(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                              float r, float g, float b, float a) {
        float[] c = {x0, y0, x1, y1, x2, y2, x3, y3};
        float[] uv = {0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f};
        float[] data = new float[6 * 8];
        int[] idx = {0, 1, 2, 0, 2, 3};
        int p = 0;
        for (int k : idx) {
            data[p++] = c[k * 2];
            data[p++] = c[k * 2 + 1];
            data[p++] = uv[k * 2];
            data[p++] = uv[k * 2 + 1];
            data[p++] = r;
            data[p++] = g;
            data[p++] = b;
            data[p++] = a;
        }
        return data;
    }

    private void uploadVerts(float[] data) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
    }

    private void ensureFbo(int w, int h) {
        if (fbo != 0 && w == fboW && h == fboH) {
            return;
        }
        if (fboTex != 0) {
            glDeleteTextures(fboTex);
        }
        if (fbo == 0) {
            fbo = glGenFramebuffers();
        }
        fboTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fboTex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fboTex, 0);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete: " + status);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        fboW = w;
        fboH = h;
    }

    private int upload(BufferedImage image, boolean nearest) {
        int w = image.getWidth();
        int h = image.getHeight();
        ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
        // Flip vertically so the top of the PNG lands at texture v = 1.
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                buffer.put((byte) ((argb >> 16) & 0xff));
                buffer.put((byte) ((argb >> 8) & 0xff));
                buffer.put((byte) (argb & 0xff));
                buffer.put((byte) ((argb >> 24) & 0xff));
            }
        }
        buffer.flip();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        int filter = nearest ? GL_NEAREST : GL_LINEAR;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        return tex;
    }

    private int solidTexture() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        return upload(image, true);
    }

    private void ortho(float left, float right, float bottom, float top) {
        float[] m = new float[16];
        m[0] = 2f / (right - left);
        m[5] = 2f / (top - bottom);
        m[10] = -1f;
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[15] = 1f;
        matrix.clear();
        matrix.put(m);
        matrix.flip();
    }

    private static int program(String vert, String frag) {
        int vs = shader(GL_VERTEX_SHADER, vert);
        int fs = shader(GL_FRAGMENT_SHADER, frag);
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        return program;
    }

    private static int shader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        String log = glGetShaderInfoLog(shader);
        if (log != null && !log.isBlank()) {
            System.err.println(log);
        }
        return shader;
    }

    private static final String VERT = """
            #version 150
            in vec2 aPos;
            in vec2 aUv;
            in vec4 aColor;
            uniform mat4 uProj;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vUv = aUv;
                vColor = aColor;
                gl_Position = uProj * vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String FRAG = """
            #version 150
            uniform sampler2D uTex;
            in vec2 vUv;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                vec4 tex = texture(uTex, vUv);
                fragColor = vec4(vColor.rgb, vColor.a) * tex;
            }
            """;
}
