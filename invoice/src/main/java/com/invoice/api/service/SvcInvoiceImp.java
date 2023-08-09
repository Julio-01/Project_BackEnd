package com.invoice.api.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoProduct;
import com.invoice.api.entity.Cart;
import com.invoice.api.entity.Invoice;
import com.invoice.api.entity.Item;
import com.invoice.api.repository.RepoCart;
import com.invoice.api.repository.RepoInvoice;
import com.invoice.api.repository.RepoItem;
import com.invoice.configuration.client.ProductClient;
import com.invoice.exception.ApiException;

@Service
public class SvcInvoiceImp implements SvcInvoice {

	@Autowired
	RepoInvoice repo;
	
	@Autowired
	RepoItem repoItem;
	
	@Autowired
	RepoCart repoCart;
	
	@Autowired
	ProductClient productCl;
	
	@Autowired
	SvcCart svcCart;

	@Override
	public List<Invoice> getInvoices(String rfc) {
		return repo.findByRfcAndStatus(rfc, 1);
	}

	@Override
	public List<Item> getInvoiceItems(Integer invoice_id) {
		return repoItem.getInvoiceItems(invoice_id);
	}

	@Override
	public ApiResponse generateInvoice(String rfc) {
		/*
		 * Requerimiento 5
		 * Implementar el método para generar una factura 
		*/
		List<Cart> products = repoCart.findByRfcAndStatus(rfc, 1);
		if(products.size() == 0) {
			throw new ApiException(HttpStatus.NOT_FOUND, "cart has no items");
		}
		try {
			List<Item> items = new ArrayList<Item>(); 
			for(int i = 0; i < products.size(); i++) {
				Item item = createItem(products.get(i), rfc);
				items.add(item);
			}
			Invoice inv = createInvoice(items, rfc);
			repo.save(inv);
			assignInvoiceIdAndSave(items, inv);
			svcCart.clearCart(rfc);
		}catch (DataIntegrityViolationException e) {
			if (e.getLocalizedMessage().contains("item"))
				throw new ApiException(HttpStatus.BAD_REQUEST, "error to generate items");
			if (e.getLocalizedMessage().contains("invoice"))
				throw new ApiException(HttpStatus.BAD_REQUEST, "error to generate invoice");
		}
		return new ApiResponse("invoice generated");
	}
	
	
	private Item createItem(Cart cart, String rfc) {
		ResponseEntity<DtoProduct> response = productCl.getProduct(cart.getGtin());
		DtoProduct product = response.getBody();
		Double product_price = product.getPrice();
		Double total = product_price * cart.getQuantity();
		Double taxes = total * 0.16;
		Item item = new Item();
		item.setGtin(cart.getGtin());
		item.setQuantity(cart.getQuantity());
		item.setUnit_price(product_price);
		item.setTotal(total);
		item.setTaxes(total * 0.16);
		item.setSubtotal(total-taxes);
		return item;
	}
	
	
	private Invoice createInvoice(List<Item> items, String rfc) {
		Invoice inv = new Invoice();
		Double subtotal = 0.0;
		Double taxes = 0.0;
		Double total = 0.0;
		for(int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			subtotal += item.getSubtotal();
			taxes += item.getTaxes();
			total += item.getTotal();
		}
		inv.setRfc(rfc);
		inv.setSubtotal(subtotal);
		inv.setTaxes(taxes);
		inv.setTotal(total);
		inv.setCreated_at(LocalDateTime.now());
		inv.setStatus(1);
		return inv;
	}
	
	
	private void assignInvoiceIdAndSave(List<Item> items, Invoice inv) {
		for(int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			ResponseEntity<DtoProduct> response = productCl.getProduct(item.getGtin());
			DtoProduct product = response.getBody();
			productCl.updateProductStock(item.getGtin(), item.getQuantity());
			item.setId_invoice(inv.getInvoice_id());
			item.setStatus(1);
			repoItem.save(item);
		}
	}

}
