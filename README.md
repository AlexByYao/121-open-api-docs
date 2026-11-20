# 121 智能化采购同步 · 开放接口文档

对外静态文档网站。文档版本1.2.0，网页整理日期2026-11-20。

`source/content.md` 是网页正文；`source/build.py` 根据正文、模板和配套OpenAPI生成根目录静态文件。页面只依赖站点内资源，支持目录、搜索、折叠示例、复制代码、打印和手机阅读。

## 本地构建

```shell
python -m pip install -r source/requirements.txt
python source/build.py --output .
python -m http.server 8765 --bind 127.0.0.1
```

以上命令在独立文档仓库根目录运行。配套契约位于 `source/specs/`。

在原121工作区维护时，文件位于 `docs-site/`：运行 `python docs-site/build.py` 生成 `docs-site/site/`，再运行 `python docs-site/prepare_publish.py` 更新独立发布目录 `docs-site/publish/`。只从独立发布目录提交和推送文档。

## GitHub Pages

发布源为 `main` 分支的仓库根目录。根目录提交 `site/` 内生成的静态文件；`.nojekyll` 使GitHub直接发布。文档源码在 `source/` 中维护。

修改 `source/content.md` 或配套契约后，在独立文档仓库运行：

```shell
python source/build.py --output .
```

检查后提交并推送；Pages自动更新。不要把任何正式密钥、客户配置、人员名单、生产数据或内部项目代码添加到公开仓库。

文档页面公开不代表业务API已上线。接口域名、客户端凭据、授权部门和回调地址由双方线下交付。
