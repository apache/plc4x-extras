/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.merlot.lector62x.api;

public class MerlotLector620ShippingPackageDTO {

    private String codguia;
    private String numeropieza;
    private String totalpiezas;
    private String oficinaorigen;
    private String oficinadestino;
    private String servicio;
    private String casillero;
    private String ruta;
    private Double peso;

    public MerlotLector620ShippingPackageDTO() {
    }

    public MerlotLector620ShippingPackageDTO(String codguia, String numeropieza, String totalpiezas, String oficinaorigen, String oficinadestino, String servicio, String casillero, String ruta, Double peso) {
        this.codguia = codguia;
        this.numeropieza = numeropieza;
        this.totalpiezas = totalpiezas;
        this.oficinaorigen = oficinaorigen;
        this.oficinadestino = oficinadestino;
        this.servicio = servicio;
        this.casillero = casillero;
        this.ruta = ruta;
        this.peso = peso;
    }

    public MerlotLector620ShippingPackageDTO(String... data) {
        this(data[0].replace("\u0002", ""),
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7].replace("\u0003", ""),
                Double.parseDouble(data[8].replace("\u0003", "").trim()));
    }

    public String getCodguia() {
        return codguia;
    }

    public void setCodguia(String codguia) {
        this.codguia = codguia;
    }

    public String getNumeropieza() {
        return numeropieza;
    }

    public void setNumeropieza(String numeropieza) {
        this.numeropieza = numeropieza;
    }

    public String getTotalpiezas() {
        return totalpiezas;
    }

    public void setTotalpiezas(String totalpiezas) {
        this.totalpiezas = totalpiezas;
    }

    public String getOficinaorigen() {
        return oficinaorigen;
    }

    public void setOficinaorigen(String oficinaorigen) {
        this.oficinaorigen = oficinaorigen;
    }

    public String getOficinadestino() {
        return oficinadestino;
    }

    public void setOficinadestino(String oficinadestino) {
        this.oficinadestino = oficinadestino;
    }

    public String getServicio() {
        return servicio;
    }

    public void setServicio(String servicio) {
        this.servicio = servicio;
    }

    public String getCasillero() {
        return casillero;
    }

    public void setCasillero(String casillero) {
        this.casillero = casillero;
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    public Double getPeso() {
        return peso;
    }

    public void setPeso(Double peso) {
        this.peso = peso;
    }

    @Override
    public String toString() {
        return "{" + "codguia=" + codguia + ", numeropieza=" + numeropieza + ", totalpiezas=" + totalpiezas + ", oficinaorigen=" + oficinaorigen + ", oficinadestino=" + oficinadestino + ", servicio=" + servicio + ", casillero=" + casillero + ", ruta=" + ruta + ", peso=" + peso + '}';
    }

}
