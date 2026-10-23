# 未使用反射序列化，无需额外规则。
# 保留自定义 View 的构造函数（若后续启用布局预览/资源反射）。
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
