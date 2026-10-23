FROM python:3.13-slim
RUN useradd --create-home --uid 10001 baro && mkdir /data && chown baro /data
WORKDIR /app
COPY baro.py /app/baro.py
USER baro
ENV BARO_BIND=0.0.0.0 PORT=8787 BARO_DB=/data/baro.sqlite PYTHONDONTWRITEBYTECODE=1
EXPOSE 8787
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8787/healthz',timeout=2)"
CMD ["python", "baro.py"]
