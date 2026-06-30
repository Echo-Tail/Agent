# VisualStrategyService Prompt 重写方案

参考 gpt-image-2 prompting guide 的结构化规范（Background/scene → Subject → Key details → Constraints），将每个 role 的 prompt 从一句话扩展为完整的生图 prompt。

## 修改范围

`VisualStrategyService.java` 中的 `promptFor(role, cn)` 方法重写。

`galleryStructureCn/En` 保持不变（用于 `visual_structure` 字段的简洁描述）。

## Gallery Role Prompts

### why_buy

```java
case "why_buy" -> {
    if (cn) return """
        场景：半幅驾驶座视角，左右对比构图。
        左侧：原车旧中控台，小屏幕、物理按键林立、哑光塑料质感，暖暗色调营造陈旧氛围。
        右侧：升级后的智能大屏，QLED触摸屏亮屏状态显示导航和音乐UI界面，清晰触控反馈。
        中间用发光箭头从旧指向新，强化升级路径。
        光线：柔光箱车内照明，主光源从挡风玻璃方向照射，自然漫反射。
        视角：低角度驾驶座人眼高度，左右各占画面50%。
        色调：左侧暖暗（陈旧感），右侧明亮冷白（科技感）。
        风格：专业汽车产品摄影，超写实，景深适中（f/4-f/5.6）。
        质量：8K超高清，细节锐利，金属质感真实。
        文字：图片底部预留空间，允许叠加英文短文字（headline + subhead）。
        约束：禁止文字遮挡产品主体，禁止杂乱背景元素，禁止水印。""";
    return """
        Scene: Half-width driver's seat perspective, split before/after comparison composition.
        Left side: factory dashboard with small screen, physical buttons, matte plastic texture, warm dim lighting creating dated atmosphere.
        Right side: upgraded QLED touchscreen displaying navigation and music UI, clear touch feedback.
        Center: glowing arrow connecting old to new, emphasizing upgrade path.
        Lighting: softbox in-car lighting, main light from windshield direction, natural diffuse reflection.
        Perspective: low angle at driver's eye level, each side occupying 50% of frame.
        Color tone: warm dim on left (dated feel), bright cool white on right (tech feel).
        Style: professional automotive product photography, photorealistic, moderate depth of field (f/4-f/5.6).
        Quality: 8K ultra HD, razor sharp details, realistic metal texture.
        Text: reserve bottom space for English text overlays (headline + subhead).
        Constraints: no text covering product subjects, no cluttered background, no watermarks. """;
}
```

### core_connection

```java
case "core_connection" -> {
    if (cn) return """
        场景：三层连接结构图。
        上层：手机屏幕镜像显示CarPlay/Android Auto界面，屏幕反光自然。
        中层：无线信号波浪图标从手机延伸至车机，蓝色数据流光效。
        下层：车内中控仪表台，触摸屏显示手机应用界面。
        光线：车内环境光为主，手机和屏幕自发光形成视觉焦点。
        视角：驾驶座斜45度视角，同时看到手机和车机屏幕。
        色调：深蓝+黑色背景衬托科技感，屏幕内容高饱和。
        风格：产品展示图，清晰干净，超写实。
        质量：8K，细节锐利。
        文字：顶部或底部允许英文短文字。
        约束：不要过多杂光，不要遮挡屏幕内容，不要水印。""";
    return """
        Scene: three-layer connection composition.
        Top layer: phone screen mirroring CarPlay/Android Auto interface with natural screen reflection.
        Middle layer: wireless signal wave icon extending from phone to head unit, blue data stream light effect.
        Bottom layer: car dashboard center console with touchscreen displaying phone apps.
        Lighting: ambient car lighting, phone and screen self-illumination as visual focus.
        Perspective: driver's seat 45-degree angle, both phone and head unit screen visible.
        Color tone: dark blue + black background for tech feel, screen content high saturation.
        Style: product showcase, clean and clear, photorealistic.
        Quality: 8K, sharp details.
        Text: allow English text overlays at top or bottom.
        Constraints: no excessive light flares, no blocking screen content, no watermarks. """;
}
```

### screen_experience

```java
case "screen_experience" -> {
    if (cn) return """
        场景：车内驾驶座视角，正对中控台大屏。
        触摸屏亮屏显示高清导航地图界面，路线清晰可见，附带实时路况彩色标记。
        屏幕边缘薄边框设计突出，UI图标具有玻璃质感光效。
        方向盘边缘入画，提供驾驶座参考框架。
        光线：屏幕自发光为画面主光源，车内环境光柔和补充，屏幕表面无过度反光。
        视角：驾驶座正视角，屏幕居中占画面60%，方向盘在左侧入画10%。
        色调：屏幕深色模式UI搭配高对比彩色地图，车内微暗突出屏幕。
        风格：汽车内饰产品摄影，超写实，屏幕无反光眩光。
        质量：8K超高清，屏幕文字像素级清晰。
        文字：允许英文短文字叠加在画面底部深色区域。
        约束：屏幕不能有过曝或反光条纹，不能变形失真。""";
    return """
        Scene: in-car driver's seat perspective, facing center console large screen.
        QLED touchscreen displaying high-definition navigation map with clear route and live traffic color markers.
        Thin bezel design visible, UI icons with glass texture lighting effects.
        Steering wheel edge framed in left side for contextual reference.
        Lighting: screen self-illumination as main light source, soft ambient fill, no excessive reflections on screen surface.
        Perspective: driver's head-on view, screen centered occupying 60% of frame, steering wheel entering 10% from left.
        Color tone: dark mode UI with high-contrast colored map, dim interior to highlight screen.
        Style: automotive interior product photography, photorealistic, no screen glare.
        Quality: 8K ultra HD, pixel-sharp screen text legibility.
        Text: allow English text overlays on dark areas at bottom of frame.
        Constraints: no screen overexposure or reflection streaks, no distortion. """;
}
```

### safety_scene

```java
case "safety_scene" -> {
    if (cn) return """
        场景：倒车场景，车内视角看向后方。
        屏幕显示倒车影像画面，带动态轨迹引导线，后方障碍物清晰可见。
        后视镜中可以看到车尾和停车场环境。
        也可替换为免提通话场景：屏幕显示通话界面，方向盘上有接听按钮指示。
        光线：倒车时自然光+屏幕背光，夜间模式微暗照明。
        视角：驾驶座回头看屏幕+后视镜的复合视角。
        色调：画面偏暖色（倒车灯照明），屏幕引导线为亮黄/绿色。
        风格：真实驾驶场景抓拍感，不过度美化。
        质量：4K起步，细节清晰可辨。
        文字：底部短文字描述安全功能。
        约束：不要恐怖或碰撞画面，不要水印。""";
    return """
        Scene: reverse parking scenario, in-car view looking rearward.
        Screen displaying rearview camera feed with dynamic trajectory guidelines, obstacles clearly visible behind.
        Rearview mirror showing tailgate and parking environment.
        Alternative: hands-free calling scene with call interface on screen and answer button indicator on steering wheel.
        Lighting: natural light during reverse + screen backlight, dim ambient for nighttime mode.
        Perspective: driver looking at screen + rearview mirror composite view.
        Color tone: warm tones (reverse light illumination), guideline bright yellow/green.
        Style: realistic driving scene capture, not over-styled.
        Quality: 4K minimum, clearly discernible details.
        Text: short safety feature description text at bottom.
        Constraints: no crash or collision imagery, no watermarks. """;
}
```

### entertainment_audio

```java
case "entertainment_audio" -> {
    if (cn) return """
        场景：车内驾驶座视角，屏幕显示音乐播放界面，专辑封面清晰。
        音频均衡器可视化效果在屏幕下方或周围，声音和音乐的氛围感。
        车内环境暗示舒适放松的氛围，可能看到杯架和内饰细节。
        光线：屏幕音乐界面发光为主，车内氛围灯带补充（可选蓝色或RGB）。
        视角：驾驶座斜视角，屏幕居中偏左，留出空间给音响/门板视觉元素。
        色调：深色背景衬托音乐界面色彩，专辑封面色彩丰富。
        风格：生活方式+产品摄影融合，有温度不冰冷。
        质量：8K。
        文字：底部或顶部短文字。
        约束：不要嘈杂混乱的元素，不要水印。""";
    return """
        Scene: in-car driver's seat perspective, screen displaying music player interface with album art visible.
        Audio equalizer visualization effects below or around screen, conveying sound and music atmosphere.
        Interior environment suggesting comfortable relaxed mood, cup holder and trim details visible.
        Lighting: screen music interface glow as primary, ambient lighting strips as supplement (optional blue/RGB).
        Perspective: driver's diagonal view, screen centered slightly left, space for speaker/door panel visual elements.
        Color tone: dark background complementing music interface colors, rich album art colors.
        Style: lifestyle + product photography blend, warm not cold.
        Quality: 8K.
        Text: short text at bottom or top.
        Constraints: no cluttered noisy elements, no watermarks. """;
}
```

### compatibility_installation

```java
case "compatibility_installation" -> {
    if (cn) return """
        场景：信息图风格，展示产品与车型适配关系。
        上位：产品主体图，带标注线和尺寸说明，做工细节清晰可见。
        中位：车型适配列表/图示，对应车型图标或剪影，兼容性信息一目了然。
        下位：安装示意图，展示线束接口对应关系、安装步骤简化示意。
        光线：均匀明亮产品摄影照明，信息图区域纯色背景。
        视角：平视图，信息图布局从上到下，符合阅读习惯。
        色调：白色/浅灰背景，蓝色为主色调辅助线，产品彩图。
        风格：干净的信息图风格，兼有产品摄影质感。
        质量：8K产品图+矢量质感信息图。
        文字：布局内嵌中文/英文说明文字，清晰可读。
        约束：不要过多装饰元素，保持信息清晰第一。""";
    return """
        Scene: infographic style showing product-to-vehicle fitment relationship.
        Top: hero product image with callout lines and dimension labels, build quality details clearly visible.
        Middle: vehicle fitment list/diagram with corresponding model icons or silhouettes, compatibility info at a glance.
        Bottom: installation diagram showing wiring harness connections and simplified step guide.
        Lighting: even bright product photography lighting, solid color background for infographic areas.
        Perspective: flat view, infographic layout top-to-bottom following reading flow.
        Color tone: white/light gray background, blue accent guide lines, product in full color.
        Style: clean infographic with product photography quality.
        Quality: 8K product image + vector-quality infographic.
        Text: embedded English/Chinese annotation text, clearly legible.
        Constraints: no excessive decorative elements, clarity first. """;
}
```

### feature_spotlight

```java
case "feature_spotlight" -> {
    if (cn) return """
        场景：产品核心部件微距特写。
        处理器芯片/电路板细节：精密电路走线，焊接点光滑均匀，散热片金属质感。
        或触摸屏边缘：玻璃面板与边框结合处，极窄边框设计，屏幕像素点阵（仅微距可见）。
        或接口部分：USB/HDMI接口金属光泽，内部结构精密。
        光线：高角度定向光，突出表面纹理和金属质感，微距景深极浅。
        视角：微距/超微距视角，极近拍摄。
        色调：冷白金属色，电路板深绿/黑色，高光控制精准不过曝。
        风格：科技产品微距摄影，实验室/专业评测画质。
        质量：超8K微距，纹理细节极致。
        文字：标注说明 text overlay 标注关键参数（如"Rockchip PX6 / 4GB RAM"）。
        约束：不要灰尘或指纹，不要模糊，不要水印。""";
    return """
        Scene: macro close-up of core product component.
        Processor chip/circuit board details: precision circuit traces, smooth even solder points, heat sink metal texture.
        Alternative: touchscreen edge: glass panel and bezel junction, ultra-slim bezel design, screen pixel array (macro visible).
        Alternative: port section: USB/HDMI port metallic sheen, precise internal structure.
        Lighting: high-angle directional light emphasizing surface texture and metallic quality, ultra-shallow macro depth of field.
        Perspective: macro/ultra-macro, extreme close-up.
        Color tone: cool white metallic, circuit board dark green/black, highlights controlled without overexposure.
        Style: tech product macro photography, lab/professional review quality.
        Quality: beyond 8K macro, extreme texture detail.
        Text: callout text overlays with key specs (e.g. "Rockchip PX6 / 4GB RAM").
        Constraints: no dust or fingerprints, no blur, no watermarks. """;
}
```

### usage_scene

```java
case "usage_scene" -> {
    if (cn) return """
        场景：真实车内驾驶场景，展示产品完整安装状态。
        中控台全景，大屏显示日常使用界面（导航/音乐/设置等）。
        驾驶座部分可见，人手上屏幕操作（可选），呈现真实使用感。
        车内环境真实，可能包含车顶、A柱、仪表盘等。
        光线：自然日光从车窗射入，漫反射在屏幕和仪表台表面。
        视角：后排中间或副驾视角，看到整个中控台全景。
        色调：自然日光照色温，车内饰原色还原准确。
        风格：真实场景产品植入摄影，自然不摆拍，生活方式感。
        质量：8K。
        文字：底部短英文字幕。
        约束：不要杂乱物品，不要过度修饰，不要水印。""";
    return """
        Scene: real in-car driving environment showing product in full installed state.
        Full dashboard view, large screen displaying everyday interface (navigation/music/settings).
        Driver seat partially visible, hand operating screen (optional), conveying real usage feel.
        Interior environment authentic, may include roofliner, A-pillar, instrument cluster.
        Lighting: natural daylight entering through windows, diffuse reflection on screen and dashboard surface.
        Perspective: rear center or passenger seat view, showing full dashboard panorama.
        Color tone: natural daylight color temperature, accurate interior color reproduction.
        Style: real scene product placement photography, natural unstaged, lifestyle feel.
        Quality: 8K.
        Text: short English text overlay at bottom.
        Constraints: no clutter, no over-styling, no watermarks. """;
}
```
