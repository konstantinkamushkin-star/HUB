import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { isAdminRole } from '../auth/rbac/admin-roles';

@Injectable()
export class ShopCommerceService {
  constructor(
    @InjectDataSource()
    private readonly dataSource: DataSource,
  ) {}

  private async assertShopOwner(
    shopId: string,
    userId: string,
    role?: string,
  ) {
    if (isAdminRole(role)) {
      const s = await this.dataSource.query(
        `SELECT id FROM shops WHERE id = $1::uuid LIMIT 1`,
        [shopId],
      );
      if (!s.length) throw new NotFoundException('Shop not found');
      return;
    }
    const rows = await this.dataSource.query(
      `SELECT owner_id FROM shops WHERE id = $1::uuid LIMIT 1`,
      [shopId],
    );
    if (!rows.length) throw new NotFoundException('Shop not found');
    if (String(rows[0].owner_id ?? '') !== userId) {
      throw new ForbiddenException('Not the shop owner');
    }
  }

  async listProducts(shopId: string, userId: string, role?: string) {
    await this.assertShopOwner(shopId, userId, role);
    return this.dataSource.query(
      `
      SELECT id, shop_id AS "shopId", name, price::float8 AS price, stock, status, updated_at AS "updatedAt"
      FROM shop_products WHERE shop_id = $1::uuid ORDER BY updated_at DESC
      `,
      [shopId],
    );
  }

  async upsertProduct(
    shopId: string,
    body: Record<string, unknown>,
    userId: string,
    role?: string,
  ) {
    await this.assertShopOwner(shopId, userId, role);
    const id = (body.id as string | undefined)?.trim();
    const name = String(body.name ?? '').trim();
    if (!name) throw new BadRequestException('name required');
    const price = Number(body.price ?? 0);
    const stock = Number(body.stock ?? 0);
    const status = String(body.status ?? 'active');
    if (id) {
      const rows = await this.dataSource.query(
        `
        UPDATE shop_products SET name = $2, price = $3, stock = $4, status = $5, updated_at = NOW()
        WHERE id = $1::uuid AND shop_id = $6::uuid
        RETURNING id, shop_id AS "shopId", name, price::float8 AS price, stock, status, updated_at AS "updatedAt"
        `,
        [id, name, price, stock, status, shopId],
      );
      if (!rows.length) throw new NotFoundException('Product not found');
      return rows[0];
    }
    const rows = await this.dataSource.query(
      `
      INSERT INTO shop_products (shop_id, name, price, stock, status)
      VALUES ($1::uuid, $2, $3, $4, $5)
      RETURNING id, shop_id AS "shopId", name, price::float8 AS price, stock, status, updated_at AS "updatedAt"
      `,
      [shopId, name, price, stock, status],
    );
    return rows[0];
  }

  async listOrders(shopId: string, userId: string, role?: string) {
    await this.assertShopOwner(shopId, userId, role);
    return this.dataSource.query(
      `
      SELECT id, shop_id AS "shopId", customer_name AS "customerName", item_count AS "itemCount",
             total::float8 AS total, status, created_at AS "createdAt"
      FROM shop_orders WHERE shop_id = $1::uuid ORDER BY created_at DESC
      `,
      [shopId],
    );
  }

  async upsertOrder(
    shopId: string,
    body: Record<string, unknown>,
    userId: string,
    role?: string,
  ) {
    await this.assertShopOwner(shopId, userId, role);
    const id = (body.id as string | undefined)?.trim();
    const customerName = String(body.customerName ?? '').trim();
    if (!customerName) throw new BadRequestException('customerName required');
    const itemCount = Number(body.itemCount ?? 1);
    const total = Number(body.total ?? 0);
    const status = String(body.status ?? 'new');
    if (id) {
      const rows = await this.dataSource.query(
        `
        UPDATE shop_orders SET customer_name = $2, item_count = $3, total = $4, status = $5
        WHERE id = $1::uuid AND shop_id = $6::uuid
        RETURNING id, shop_id AS "shopId", customer_name AS "customerName", item_count AS "itemCount",
          total::float8 AS total, status, created_at AS "createdAt"
        `,
        [id, customerName, itemCount, total, status, shopId],
      );
      if (!rows.length) throw new NotFoundException('Order not found');
      return rows[0];
    }
    const rows = await this.dataSource.query(
      `
      INSERT INTO shop_orders (shop_id, customer_name, item_count, total, status)
      VALUES ($1::uuid, $2, $3, $4, $5)
      RETURNING id, shop_id AS "shopId", customer_name AS "customerName", item_count AS "itemCount",
        total::float8 AS total, status, created_at AS "createdAt"
      `,
      [shopId, customerName, itemCount, total, status],
    );
    return rows[0];
  }
}
