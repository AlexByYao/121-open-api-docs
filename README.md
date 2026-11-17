# 121 智能化采购同步 · 开放接口文档

对外静态文档网站。文档版本1.1.0，网页整理日期2026-11-17。

`content.md` 是网页正文；`build.py` 根据正文、模板和配套OpenAPI生成 `site/`。页面只依赖站点内资源，支持目录、搜索、折叠示例、复制代码、打印和手机阅读。

## 本地构建

```shell
python -m pip install -r requirements.txt
python build.py
python -m http.server 8765 --directory site --bind 127.0.0.1
```

在本121工作区构建时，脚本从上层 `docs/` 读取已核对的OpenAPI；在独立文档仓库中构建时读取 `specs/`。

## GitHub Pages

发布源为 `main` 分支的仓库根目录。根目录提交 `site/` 内生成的静态文件；`.nojekyll` 使GitHub直接发布。文档源码在 `source/` 中维护。

修改 `source/content.md` 或配套契约后，在独立文档仓库运行：

```shell
python source/build.py --output .
```

检查后提交并推送；Pages自动更新。不要把任何正式密钥、客户配置、人员名单、生产数据或内部项目代码添加到公开仓库。

文档页面公开不代表业务API已上线。接口域名、客户端凭据、授权部门和回调地址由双方线下交付。
