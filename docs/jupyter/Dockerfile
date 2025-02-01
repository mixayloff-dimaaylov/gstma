# Copyright 2023 mixayloff-dimaaylov at github dot com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM jupyter/pyspark-notebook:spark-3.3.1

# Install Almond kernel

# Workaround to systemwide installation of kernel
# Ref: https://jupyter-docker-stacks.readthedocs.io/en/latest/using/recipes.html#using-sudo-within-a-container
USER root
ENV GRANT_SUDO=yes

# Cannot run Almond on openjdk17 due error
RUN apt update && \
    apt install -y curl openjdk-8-jre-headless ca-certificates-java && \
    update-alternatives --set java /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java

# Ref: https://github.com/almond-sh/almond/blob/v0.13.3/docs/pages/quick-start-install.md
# Ref: https://github.com/almond-sh/almond/blob/v0.13.3/docs/pages/install-options.md
RUN curl -Lo coursier https://git.io/coursier-cli && \
    chmod +x coursier && \
    ./coursier launch --fork almond:0.13.2 --scala 2.12.17 -- \
    --install --global --force \
    --id scala2.12 --display-name 'Scala 2.12' && \
    rm -f coursier && \
    fix-permissions "/home/${NB_USER}"

USER $NB_USER

# Install JupyterLab extensions
# git, jupyterlab-lsp already installed
COPY environment.yaml /tmp
RUN mamba env update -f=/tmp/environment.yaml && \
    jupyter labextension install jupyterlab-plotly
