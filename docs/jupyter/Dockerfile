FROM jupyter/pyspark-notebook:spark-3.3.1

USER $NB_USER

# Install JupyterLab extensions
# git and curl already installed
RUN mamba install -y -c conda-forge jupyterlab-git
