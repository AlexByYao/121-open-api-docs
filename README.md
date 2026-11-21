# 121 接口交接文档

121开放接口参考文档，包含账号与字典查询、采购草稿操作、删除通知、请求示例及联调资料。适用接口版本2.6.0，文档更新日期2026-11-21。

`source/content.md` 是网页正文；`source/build.py` 根据正文、模板和配套OpenAPI生成根目录静态文件。页面只依赖站点内资源，支持目录、搜索、折叠示例、复制代码、打印和手机阅读。

## 本地构建

```shell
python -m pip install -r source/requirements.txt
python source/build.py --output .
python -m http.server 8765 --bind 127.0.0.1
```

以上命令在独立文档仓库根目录运行。配套契约位于 `source/specs/`。

## GitHub Pages

发布源为 `main` 分支的仓库根目录。根目录提交 `site/` 内生成的静态文件；`.nojekyll` 使GitHub直接发布。文档源码在 `source/` 中维护。

修改 `source/content.md` 或配套契约后，在独立文档仓库运行：

```shell
python source/build.py --output .
```

检查后提交并推送；Pages自动更新。不要把任何正式密钥、客户配置、人员名单、生产数据或内部项目代码添加到公开仓库。

接入凭据、可访问部门及回调地址由接入双方确认。
