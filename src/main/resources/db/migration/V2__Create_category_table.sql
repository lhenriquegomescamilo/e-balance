-- V2: Create category table and link to transactions
CREATE TABLE IF NOT EXISTS category
(
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

-- Insert predefined categories (use NULL for id to let SQLite auto-generate)
INSERT INTO category (name) VALUES 
       ('Desconhecida'),
       ('Moradia'),
       ('Restaurante'),
       ('Biscoitos'),
       ('Supermercado'),
       ('Escola dos filhos'),
       ('Delivery'),
       ('Roupa'),
       ('Eletrônicos'),
       ('Cabelo'),
       ('Transporte'),
       ('Carro'),
       ('Segurança Social'),
       ('IRS'),
       ('Salário'),
       ('Contabilista'),
       ('Arrendamento'),
       ('Telefonia'),
       ('Energia'),
       ('Água'),
       ('Personal Trainer'),
       ('Barbeiro'),
       ('Via Verde'),
       ('Seguro de carro'),
       ('Doações'),
       ('Assinaturas'),
       ('Taxa Bancária'),
       ('Turismo'),
       ('Portagem'),
       ('Lazer'),
       ('Cartão'),
       ('Estética'),
       ('Investimento'),
       ('Farmácia'),
       ('Levantamento'),
       ('Lavanderia'),
       ('Combustível'),
       ('Hospital'),
       ('Presente'),
       ('Lava Rápido'),
       ('Imposto'),
       ('Educação'),
       ('Seguro'),
       ('Transferências Brasil'),
       ('Ginásio');

-- Add category_id column to transactions
ALTER TABLE transactions ADD COLUMN category_id INTEGER DEFAULT 0;

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_transactions_category_id ON transactions(category_id);
