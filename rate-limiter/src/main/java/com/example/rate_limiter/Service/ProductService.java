package com.example.rate_limiter.Service;
import com.example.rate_limiter.model.Product;
import java.util.*;
import org.springframework.stereotype.Service;

@Service 
public class ProductService {

  List<Product> products = new ArrayList<>(Arrays.asList(new Product(101, "Samsung", 100000), new Product(102, "REDMI", 10000), new Product(103, "Moto", 19000)));
  public List<Product> getProducts(){
    return products;
  }

  public Product getProductById(int prodId){
    return products.stream()  
    .filter(p -> p.getProdId() == prodId)
    .findFirst().get();
  }
  
  public void addProduct(Product prod){
    products.add(prod);
  }

}
