
### 开发踩坑记录
- 原由：不想用Tailwind CSS，认为AI无法理解其原子类样式效果，调试困难。
- 现象：DeepSeek V4 始终不识别代码中已引入的Tailwind（含CDN链接），换会话也判定为“未使用Tailwind”。
- 根因：本地打开的是旧文件缓存，未加载AI修改后的最新代码；手动检索class时基于缓存文件未命中，误判为AI识别问题。

- 契机：我希望自己手调代码的时候，通过关键词检索 class ，结果没检索到，才觉得当前的旧版本的代码与 ai 描述对不上。



![image-20260505002846459](https://gitee.com/Seniorsy/pic-go/raw/master/typora/image-20260505002846459.png)


