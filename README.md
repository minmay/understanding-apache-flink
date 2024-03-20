# Understanding Apache Flink
Repository used for my presentation on "Understanding Apache Flink" and demo source code.

### Deployment

```shell
minikube start --memory=24576 --cpus=6
minikube dashboard
```

```shell
kubectl create namespace understanding-apache-flink
kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml     
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.7.0/ -n understanding-apache-flink
helm install --set webhook.create=false flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator -n understanding-apache-flink
kubectl create -f 'https://strimzi.io/install/latest?namespace=understanding-apache-flink' -n understanding-apache-flink
kubectl apply -f conf/deployment-understanding-apache-flink.yaml -n understanding-apache-flink
kubectl apply -f conf/services-understanding-apache-flink.yaml -n understanding-apache-flink
kubectl apply -f conf/flink-operator-understanding-apache-flink.yaml -n understanding-apache-flink

```
```shell
minikube tunnel
```
### Teardown
```shell
kubectl delete -f conf/flink-operator-understanding-apache-flink.yaml -n understanding-apache-flink
kubectl delete -f conf/services-understanding-apache-flink.yaml -n understanding-apache-flink
kubectl delete -f conf/deployment-understanding-apache-flink.yaml -n understanding-apache-flink
helm uninstall flink-kubernetes-operator -n understanding-apache-flink
helm repo remove flink-operator-repo -n understanding-apache-flink
kubectl delete -f 'https://strimzi.io/install/latest?understanding-apache-flink' -n understanding-apache-flink
kubectl delete -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
kubectl delete namespace understanding-apache-flink
```

External Kafka clients such as an IDE should use ip of `data-gen-agent-kafka-extlb-bootstrap` which
can be viewed with the following command.
```shell
kubectl get services -n understanding-apache-flink | awk '{if (match($1, /^understanding\-apache\-flink\-kafka\-extlb\-bootstrap/) > 0){print $4.":9094"}}'  
```

### Kafka UI
First port-forward Kafka UI
```shell
kubectl port-forward pods/$(kubectl get pods -n understanding-apache-flink | awk '{if (match($1, /^kafka\-ui\-/) > 0){print $1}}') 58080:8080 -n understanding-apache-flink  
```
Then open it.
```shell
open http://localhost:58080
```

```bash
kubectl port-forward svc/understanding-apache-flink-deployment-rest 8081 -n understanding-apache-flink
```

```bash
open http://localhost:8081
```

### Localstack
First port-forward Localstack
```shell
kubectl port-forward pods/$(kubectl get pods -n understanding-apache-flink | awk '{if (match($1, /^localstack\-/) > 0){print $1}}') 32280:4566 -n understanding-apache-flink  
```

```shell
aws --endpoint-url http://localhost:32280 s3 ls    
```

