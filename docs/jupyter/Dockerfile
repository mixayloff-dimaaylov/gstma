FROM jupyter/pyspark-notebook:spark-3.3.1

# Install Almond kernel
# curl already installed

# Workaround to systemwide installation of kernel
# Ref: https://jupyter-docker-stacks.readthedocs.io/en/latest/using/recipes.html#using-sudo-within-a-container
USER root
ENV GRANT_SUDO=yes

# Cannot run Almond on openjdk17 due error
RUN apt update && \
    apt install -y openjdk-8-jre-headless ca-certificates-java && \
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
# git already installed
RUN mamba install -y -c conda-forge jupyterlab-git && \
    jupyter labextension install jupyterlab-plotly
