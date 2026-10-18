/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.client.rendering.fluid;

import java.util.IdentityHashMap;
import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.material.Fluid;

import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderingRegistryImpl;

@Mixin(FluidStateModelSet.class)
public abstract class FluidStateModelSetMixin {
	// Neo: add Fabric's fluid models before NeoForge gathers models from RegisterFluidModelsEvent, so that
	// NeoForge's missing-model check sees them and Fabric registrations can still override water/lava.
	@WrapOperation(method = "bake", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;gatherFluidModels(Ljava/util/Map;Lnet/minecraft/client/resources/model/sprite/MaterialBaker;)Ljava/util/Map;"))
	private static Map<Fluid, FluidModel> addFabricModels(Map<Fluid, FluidModel> vanillaModels, MaterialBaker materials, Operation<Map<Fluid, FluidModel>> original) {
		Map<Fluid, FluidModel> models = new IdentityHashMap<>(vanillaModels);
		Map<FluidModel.Unbaked, FluidModel> bakedModels = new IdentityHashMap<>();

		for (Map.Entry<Fluid, FluidModel.Unbaked> entry : FluidRenderingRegistryImpl.getUnbakedModels().entrySet()) {
			FluidModel model = bakedModels.get(entry.getValue());

			if (model == null) {
				model = entry.getValue().bake(materials, () -> entry.getKey().toString());
				bakedModels.put(entry.getValue(), model);
			}

			models.put(entry.getKey(), model);
		}

		return original.call(models, materials);
	}
}
