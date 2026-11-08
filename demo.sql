-- Демонстрация MiniDB: автосалон. Таблица sales ссылается на таблицу cars.
-- Запуск: run.bat demo.sql

DROP TABLE IF EXISTS sales;
DROP TABLE IF EXISTS cars;

CREATE TABLE cars (id INT PRIMARY KEY, brand STRING, price DOUBLE, available BOOLEAN);

CREATE TABLE sales (
    id INT PRIMARY KEY,
    car_id INT REFERENCES cars(id),
    customer STRING,
    sale_date DATE
);

-- Примеры из задания
INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);
INSERT INTO cars (id, brand) VALUES (2, 'BMW');
SELECT * FROM cars;

-- Вставка нескольких записей сразу
INSERT INTO cars VALUES (3, 'Lada', 9000.5, TRUE), (4, 'Audi', 35000, TRUE);

-- Ошибка: такой первичный ключ уже есть
INSERT INTO cars VALUES (1, 'Skoda', 21000, TRUE);

SELECT brand, price FROM cars WHERE price > 20000;
UPDATE cars SET price = 30000 WHERE id = 2;
SELECT * FROM cars WHERE id = 2;

INSERT INTO sales VALUES (1, 1, 'Иванов', '2024-03-15'), (2, 3, 'Петрова', '2024-05-20');
SELECT * FROM sales WHERE sale_date >= '2024-04-01';

-- Ошибка: автомобиля с id = 42 нет
INSERT INTO sales VALUES (3, 42, 'Сидоров', '2024-07-01');

-- Ошибка: эту Toyota уже продали, на неё ссылается sales
DELETE FROM cars WHERE brand = 'Toyota';

-- Теперь удаление проходит
DELETE FROM sales WHERE car_id = 1;
DELETE FROM cars WHERE brand = 'Toyota';

-- Индекс: сравните способ поиска (search) до и после создания индекса
SELECT * FROM cars WHERE brand = 'BMW';
CREATE INDEX idx_cars_brand ON cars (brand);
SELECT * FROM cars WHERE brand = 'BMW';
