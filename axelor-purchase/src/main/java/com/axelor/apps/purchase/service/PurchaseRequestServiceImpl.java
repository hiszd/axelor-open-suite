/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.purchase.service;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.purchase.db.PurchaseOrder;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.PurchaseRequest;
import com.axelor.apps.purchase.db.PurchaseRequestLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderLineRepository;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.purchase.db.repo.PurchaseRequestRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PurchaseRequestServiceImpl implements PurchaseRequestService {

  @Inject private PurchaseRequestRepository purchaseRequestRepo;
  @Inject private PurchaseOrderService purchaseOrderService;
  @Inject private PurchaseOrderLineService purchaseOrderLineService;
  @Inject private PurchaseOrderRepository purchaseOrderRepo;
  @Inject private PurchaseOrderLineRepository purchaseOrderLineRepo;
  @Inject private AppBaseService appBaseService;

  @Transactional
  @Override
  public void confirmCart() {

    List<PurchaseRequest> purchaseRequests =
        purchaseRequestRepo
            .all()
            .filter("self.statusSelect = 1 and self.createdBy = ?1", AuthUtils.getUser())
            .fetch();

    for (PurchaseRequest purchaseRequest : purchaseRequests) {
      purchaseRequest.setStatusSelect(2);
      purchaseRequestRepo.save(purchaseRequest);
    }
  }

  @Transactional
  @Override
  public void acceptRequest(List<PurchaseRequest> purchaseRequests) {

    for (PurchaseRequest purchaseRequest : purchaseRequests) {
      purchaseRequest.setStatusSelect(3);
      purchaseRequestRepo.save(purchaseRequest);
    }
  }

  @Transactional(rollbackOn = {Exception.class})
  @Override
  public List<PurchaseOrder> generatePo(List<PurchaseRequest> purchaseRequests)
      throws AxelorException {

    List<PurchaseOrderLine> purchaseOrderLineList = new ArrayList<PurchaseOrderLine>();
    Map<String, PurchaseOrder> purchaseOrderMap = new HashMap<>();

    for (PurchaseRequest purchaseRequest : purchaseRequests) {
      // Added chunk for new setup
      Map<Object, List<PurchaseRequestLine>> lines =
          purchaseRequest.getPurchaseRequestLineList().stream()
              .collect(Collectors.groupingBy(e -> e.getSupplierUser()));
      for (Map.Entry<Object, List<PurchaseRequestLine>> group : lines.entrySet()) {
        List<PurchaseOrder> purchaseOrderList = new ArrayList<PurchaseOrder>();
        PurchaseOrder po = new PurchaseOrder();
        for (PurchaseRequestLine entry : group.getValue()) {
          String key = true ? getPurchaseOrderGroupBySupplierKey(entry) : null;
          if (key != null && purchaseOrderMap.containsKey(key)) {
            po = purchaseOrderMap.get(key);
            purchaseOrderList.add(po);
          } else {
            po = createPurchaseOrder(purchaseRequest, entry.getSupplierUser());
            purchaseOrderList.add(po);
            key = key == null ? purchaseRequest.getId().toString() : key;
            purchaseOrderMap.put(key, po);
          }

          if (po == null) {
            po = createPurchaseOrder(purchaseRequest, entry.getSupplierUser());
          }

          PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
          Product product = entry.getProduct();

          purchaseOrderLine = po != null ? getPoLineByProduct(product, po) : null;

          purchaseOrderLine =
              purchaseOrderLineService.createPurchaseOrderLine(
                  po,
                  product,
                  entry.getNewProduct() ? entry.getProductTitle() : product.getName(),
                  entry.getNewProduct() ? null : product.getDescription(),
                  entry.getQuantity(),
                  entry.getUnit());
          po.addPurchaseOrderLineListItem(purchaseOrderLine);
          purchaseOrderLineList.add(purchaseOrderLine);
          purchaseOrderLineService.compute(purchaseOrderLine, po);
        }

        po.getPurchaseOrderLineList().addAll(purchaseOrderLineList);
        purchaseOrderService.computePurchaseOrder(po);
        purchaseOrderRepo.save(po);
        purchaseRequest.setPurchaseOrderList(purchaseOrderList);
        purchaseRequestRepo.save(purchaseRequest);
      }

      /*String key = true ? getPurchaseOrderGroupBySupplierKey(purchaseRequest) : null;
      if (key != null && purchaseOrderMap.containsKey(key)) {
        purchaseOrder = purchaseOrderMap.get(key);
      } else {
        purchaseOrder = createPurchaseOrder(purchaseRequest);
        key = key == null ? purchaseRequest.getId().toString() : key;
        purchaseOrderMap.put(key, purchaseOrder);
      }

      if (purchaseOrder == null) {
        purchaseOrder = createPurchaseOrder(purchaseRequest);
      }

      for (PurchaseRequestLine purchaseRequestLine : purchaseRequest.getPurchaseRequestLineList()) {
        PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
        Product product = purchaseRequestLine.getProduct();

        purchaseOrderLine =
            purchaseOrder != null
                ? getPoLineByProduct(product, purchaseOrder)
                : null;

        purchaseOrderLine =
            purchaseOrderLineService.createPurchaseOrderLine(
                purchaseOrder,
                product,
                purchaseRequestLine.getNewProduct()
                    ? purchaseRequestLine.getProductTitle()
                    : product.getName(),
                purchaseRequestLine.getNewProduct() ? null : product.getDescription(),
                purchaseRequestLine.getQuantity(),
                purchaseRequestLine.getUnit());
        purchaseOrder.addPurchaseOrderLineListItem(purchaseOrderLine);
        purchaseOrderLineList.add(purchaseOrderLine);
        purchaseOrderLineService.compute(purchaseOrderLine, purchaseOrder);
      }
      purchaseOrder.getPurchaseOrderLineList().addAll(purchaseOrderLineList);
      purchaseOrderService.computePurchaseOrder(purchaseOrder);
      purchaseOrderRepo.save(purchaseOrder);
      purchaseRequest.setPurchaseOrder(purchaseOrder);
      purchaseRequestRepo.save(purchaseRequest);*/
    }
    List<PurchaseOrder> purchaseOrders =
        purchaseOrderMap.values().stream().collect(Collectors.toList());
    return purchaseOrders;
  }

  private PurchaseOrderLine getPoLineByProduct(Product product, PurchaseOrder purchaseOrder) {

    PurchaseOrderLine purchaseOrderLine =
        purchaseOrder.getPurchaseOrderLineList() != null
                && !purchaseOrder.getPurchaseOrderLineList().isEmpty()
            ? purchaseOrder.getPurchaseOrderLineList().stream()
                .filter(poLine -> poLine.getProduct().equals(product))
                .findFirst()
                .orElse(null)
            : null;
    return purchaseOrderLine;
  }

  protected PurchaseOrder createPurchaseOrder(PurchaseRequest purchaseRequest, Partner supplierUser)
      throws AxelorException {
    return purchaseOrderRepo.save(
        purchaseOrderService.createPurchaseOrder(
            AuthUtils.getUser(),
            purchaseRequest.getCompany(),
            null,
            supplierUser.getCurrency(),
            null,
            null,
            null,
            appBaseService.getTodayDate(purchaseRequest.getCompany()),
            null,
            supplierUser,
            null));
  }

  protected String getPurchaseOrderGroupBySupplierKey(PurchaseRequestLine purchaseRequestLine) {
    return purchaseRequestLine.getSupplierUser().getId().toString();
  }
}
