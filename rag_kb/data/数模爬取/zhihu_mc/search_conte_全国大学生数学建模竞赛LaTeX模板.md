# 全国大学生数学建模竞赛LaTeX模板
作者: 中***和 | 链接: https://zhuanlan.zhihu.com/p/2068052204158822239 | 赞同:  | 来源关键词: 数学建模竞赛

中国大学生数学建模竞赛LaTeX模板欢迎点赞、转发、关注和 Star！您的认可就是我们持之以恒的最大动力！中国大学生数学建模竞赛LaTeX模板LaTeX Template for China Undergraduate Mathematical Contest in Modeling本项目地址: https://github.com/jayxin/cumcm原项目地址: https://github.com/latexstudio/CUMCMThesis本项目在 latexstudio 的 CUMCMThesis 项目基础上修改和添加内容，并调整了项目结构，使整个项目结构更清晰，方便使用和维护。在此感谢原作者 latexstudio 的贡献！模板预览摘要:摘要正文:正文1正文2正文3正文4正文5正文6参考文献:参考文献附录:附录下载方式方法1-git 克隆git clone https://github.com/jayxin/cumcm网络不好可用中转克隆:git clone https://gh-proxy.org/https://github.com/jayxin/cumcm方法2-直接下载某版本的源码压缩包目前最新版本是 v1.0.0。下载地址:https://github.com/jayxin/cumcm/archive/refs/tags/v1.0.0.zip中转下载地址:https://gh-proxy.org/https://github.com/jayxin/cumcm/archive/refs/tags/v1.0.0.zipReleases 界面(https://github.com/jayxin/cumcm/releases) 可下载到各版本预览的 PDF。文件列表.
├── commons/ 模板
│   ├── cumcmthesis.cls 基础模板
│   └── preamble.tex 用户自定义加载宏包、命令、环境等
├── contents/ 内容
│   ├── abstract.tex 摘要
│   ├── appendix/ 附录
│   ├── info.tex 论文基本信息
│   ├── references.tex 参考文献
│   └── sections/ 正文内容
├── docs/ 文档(包括论文格式说明文档等)
│   └── 2026高教社杯全国大学生数学建模竞赛第一次通知.pdf
├── figures/ 存放论文用到的图片文件
├── fonts/ 存放字体文件
├── .gitignore git 版本控制忽略文件
├── latexmkrc latexmk 配置文件
├── LICENSE.txt 使用许可
├── main.tex **主文档(编译入口文档, Main Document)**
└── README.md 项目说明编译本地编译使用前提: 本地已装好 LaTeX 的发行版如 TeXLive已测试环境:  操作系统 - LinuxLaTeX 发行版 - TeXLive 2023方法1-用 xelatex 编译需手动编译多次，引用等内容才能正确显示。xelatex main方法2-用 latexmk 编译自动编译多次:latexmk main清理辅助文件(log、aux等):latexmk -c main清理辅助文件(log、aux等)和 pdf:latexmk -C main在线编译可使用在线的编译平台进行编译如:  https://texpage.comhttps://overleaf.com已测试平台: TeXPage, 进行编译前需保证如下设置  编译器: xelatexTeXLive 版本: 2023主文档(Main Document): main.tex文档类选项说明本项目文档类(Document Class)目前支持下面的选项:bwprint: 黑白打印。colorprint: 彩色打印(默认)。withoutpreface: 最终文档不包含前言(承诺书和编号页), 不加这个选项则默认包含。根据最新的要求，电子版文档不需要前言，请根据具体的通知和要求进行相应调整。draft: 是否嵌入图片和代码, 默认嵌入。编译最终版的文档请勿使用此选项。此选项是为了提高文章编辑过程编译器编译的速度。欢迎点赞、转发、关注和 Star！您的认可就是我们持之以恒的最大动力！